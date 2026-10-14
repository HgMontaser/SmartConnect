package com.smartconnect.apigateway.routing;

import com.smartconnect.commons.discovery.GatewayRouteMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the gateway's routes from Consul: every healthy service that publishes
 * {@link GatewayRouteMetadata#ROUTES} gets a load-balanced route for those paths. Spring Cloud
 * Gateway re-asks this locator whenever routes may have changed (see
 * {@link DiscoveredRoutesRefresher}), so services come and go without gateway edits.
 *
 * <p>Every route gets the same protection, in this order:
 * <ol>
 *   <li>rate limiting per user (or IP);</li>
 *   <li>a circuit breaker per service: after repeated infrastructure failures (connection
 *       errors, timeouts, 502/503/504) calls fail fast with a 503 for a while instead of piling
 *       up on a broken service - see {@code FallbackController};</li>
 *   <li>retries for GET only, on connection errors and 502/503 - never for writes, which might
 *       not be safe to repeat, and never on timeouts, which would just multiply the wait.</li>
 * </ol>
 * The response timeout is the gateway default unless the service asks for its own via
 * {@link GatewayRouteMetadata#TIMEOUT}. WebSocket routes get rate limiting only.
 *
 * <p>A route whose service disappears is kept for {@link #ROUTE_GRACE}, so callers get a 503
 * "unavailable" while it restarts rather than a misleading 404. Services are processed in name
 * order and a path claimed by one service is refused to any other (logged).
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class DiscoveredRoutesLocator implements RouteDefinitionLocator {

    static final Duration ROUTE_GRACE = Duration.ofMinutes(10);
    static final String FALLBACK_URI = "forward:/fallback/unavailable";
    /** Served by the gateway itself (PlatformController); no service may claim it. */
    static final String RESERVED_PREFIX = "/api/platform";
    private static final String WS_PREFIX = "ws:";

    private static final Logger log = LoggerFactory.getLogger(DiscoveredRoutesLocator.class);
    private static final String ROUTE_ID_PREFIX = "discovered-";

    private final ReactiveDiscoveryClient discoveryClient;
    private final RateLimitProperties rateLimit;
    private final Clock clock;
    private final Map<String, RememberedRoute> lastSeen = new ConcurrentHashMap<>();

    @Autowired
    public DiscoveredRoutesLocator(ReactiveDiscoveryClient discoveryClient, RateLimitProperties rateLimit) {
        this(discoveryClient, rateLimit, Clock.systemUTC());
    }

    DiscoveredRoutesLocator(ReactiveDiscoveryClient discoveryClient, RateLimitProperties rateLimit, Clock clock) {
        this.discoveryClient = discoveryClient;
        this.rateLimit = rateLimit;
        this.clock = clock;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return discoveryClient.getServices()
                .sort()
                .concatMap(service -> discoveryClient.getInstances(service).take(1))
                .collectList()
                .flatMapMany(instances -> Flux.fromIterable(toRoutes(instances)));
    }

    List<RouteDefinition> toRoutes(List<ServiceInstance> instances) {
        Instant now = clock.instant();
        Map<String, String> claimedBy = new HashMap<>();
        List<RouteDefinition> routes = new ArrayList<>(instances.stream()
                .flatMap(instance -> toRoutes(instance, claimedBy).stream())
                .peek(route -> lastSeen.put(route.getId(), new RememberedRoute(route, now)))
                .toList());

        lastSeen.values().removeIf(r -> r.seenAt().plus(ROUTE_GRACE).isBefore(now));
        lastSeen.forEach((id, remembered) -> {
            boolean live = routes.stream().anyMatch(r -> r.getId().equals(id));
            boolean pathsFree = remembered.route().getPredicates().get(0).getArgs().values().stream()
                    .noneMatch(claimedBy::containsKey);
            if (!live && pathsFree) {
                routes.add(remembered.route());
            }
        });
        return routes;
    }

    /** One HTTP route and/or one WebSocket route per service, depending on its patterns. */
    private List<RouteDefinition> toRoutes(ServiceInstance instance, Map<String, String> claimedBy) {
        String service = instance.getServiceId();
        Map<String, String> metadata = instance.getMetadata();
        boolean allWebsocket = "ws".equals(metadata.getOrDefault(GatewayRouteMetadata.SCHEME, "http"));
        List<String> http = new ArrayList<>();
        List<String> websocket = new ArrayList<>();
        for (String raw : metadata.getOrDefault(GatewayRouteMetadata.ROUTES, "").split(",")) {
            String pattern = raw.trim();
            boolean ws = allWebsocket || pattern.startsWith(WS_PREFIX);
            pattern = pattern.startsWith(WS_PREFIX) ? pattern.substring(WS_PREFIX.length()).trim() : pattern;
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.startsWith(RESERVED_PREFIX)) {
                log.warn("{} asked for route {} which is reserved for the gateway - ignoring it", service, pattern);
                continue;
            }
            String owner = claimedBy.putIfAbsent(pattern, service);
            if (owner != null && !owner.equals(service)) {
                log.warn("{} asked for route {} which {} already serves - ignoring it", service, pattern, owner);
                continue;
            }
            (ws ? websocket : http).add(pattern);
        }

        List<RouteDefinition> routes = new ArrayList<>();
        if (!http.isEmpty()) {
            RouteDefinition route = route(ROUTE_ID_PREFIX + service, "lb://" + service, http);
            route.setFilters(List.of(rateLimiter(), circuitBreaker(service), retry()));
            String timeout = metadata.getOrDefault(GatewayRouteMetadata.TIMEOUT, "").trim();
            if (!timeout.isEmpty()) {
                try {
                    route.setMetadata(Map.of("response-timeout", DurationStyle.detectAndParse(timeout).toMillis()));
                } catch (IllegalArgumentException e) {
                    log.warn("{} published an invalid timeout '{}' - using the gateway default", service, timeout);
                }
            }
            routes.add(route);
        }
        if (!websocket.isEmpty()) {
            // WebSockets: rate limiting on the handshake only - a breaker/retry/timeout makes no sense for a long-lived stream.
            RouteDefinition route = route(ROUTE_ID_PREFIX + service + (http.isEmpty() ? "" : "-ws"), "lb:ws://" + service, websocket);
            route.setFilters(List.of(rateLimiter()));
            routes.add(route);
        }
        return routes;
    }

    private static RouteDefinition route(String id, String uri, List<String> patterns) {
        RouteDefinition route = new RouteDefinition();
        route.setId(id);
        route.setUri(URI.create(uri));
        route.setPredicates(List.of(new PredicateDefinition("Path=" + String.join(",", patterns))));
        return route;
    }

    private FilterDefinition rateLimiter() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("redis-rate-limiter.replenishRate", String.valueOf(rateLimit.getReplenishRate()));
        args.put("redis-rate-limiter.burstCapacity", String.valueOf(rateLimit.getBurstCapacity()));
        args.put("redis-rate-limiter.requestedTokens", String.valueOf(rateLimit.getRequestedTokens()));
        args.put("key-resolver", "#{@userOrIpKeyResolver}");
        return filter("RequestRateLimiter", args);
    }

    /** One breaker per service, tuned under resilience4j.circuitbreaker.* in Consul. */
    private FilterDefinition circuitBreaker(String service) {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("name", service);
        args.put("fallbackUri", FALLBACK_URI);
        args.put("statusCodes", "BAD_GATEWAY,SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT");
        return filter("CircuitBreaker", args);
    }

    private FilterDefinition retry() {
        Map<String, String> args = new LinkedHashMap<>();
        args.put("retries", "2");
        args.put("methods", "GET");
        args.put("statuses", "BAD_GATEWAY,SERVICE_UNAVAILABLE");
        args.put("series", "");
        args.put("exceptions", "java.io.IOException");
        args.put("backoff.firstBackoff", "50ms");
        args.put("backoff.maxBackoff", "500ms");
        args.put("backoff.factor", "2");
        args.put("backoff.basedOnPreviousValue", "false");
        return filter("Retry", args);
    }

    private static FilterDefinition filter(String name, Map<String, String> args) {
        FilterDefinition filter = new FilterDefinition();
        filter.setName(name);
        filter.setArgs(args);
        return filter;
    }

    private record RememberedRoute(RouteDefinition route, Instant seenAt) {
    }
}
