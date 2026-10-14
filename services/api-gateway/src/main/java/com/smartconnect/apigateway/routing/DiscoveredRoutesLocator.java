package com.smartconnect.apigateway.routing;

import com.smartconnect.commons.discovery.GatewayRouteMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the gateway's routes from Consul: every healthy service that publishes
 * {@link GatewayRouteMetadata#ROUTES} gets a load-balanced route for those paths, with the
 * fleet-wide rate limiter. Spring Cloud Gateway re-asks this locator whenever Consul's
 * catalog changes, so a service that starts, stops or changes its routes is reflected
 * within seconds - no gateway config edit or restart.
 *
 * <p>Services are processed in name order and a path pattern claimed by one service is
 * refused to any other (logged), so two services can never silently fight over a path.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class DiscoveredRoutesLocator implements RouteDefinitionLocator {

    private static final Logger log = LoggerFactory.getLogger(DiscoveredRoutesLocator.class);
    private static final String ROUTE_ID_PREFIX = "discovered-";

    private final ReactiveDiscoveryClient discoveryClient;
    private final RateLimitProperties rateLimit;

    public DiscoveredRoutesLocator(ReactiveDiscoveryClient discoveryClient, RateLimitProperties rateLimit) {
        this.discoveryClient = discoveryClient;
        this.rateLimit = rateLimit;
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
        Map<String, String> claimedBy = new HashMap<>();
        return instances.stream()
                .map(instance -> toRoute(instance, claimedBy))
                .filter(Objects::nonNull)
                .toList();
    }

    private RouteDefinition toRoute(ServiceInstance instance, Map<String, String> claimedBy) {
        String service = instance.getServiceId();
        String routes = instance.getMetadata().getOrDefault(GatewayRouteMetadata.ROUTES, "");
        List<String> patterns = Arrays.stream(routes.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .filter(p -> {
                    String owner = claimedBy.putIfAbsent(p, service);
                    if (owner != null && !owner.equals(service)) {
                        log.warn("{} asked for route {} which {} already serves - ignoring it", service, p, owner);
                        return false;
                    }
                    return true;
                })
                .toList();
        if (patterns.isEmpty()) {
            return null;
        }

        String scheme = instance.getMetadata().getOrDefault(GatewayRouteMetadata.SCHEME, "http");
        RouteDefinition route = new RouteDefinition();
        route.setId(ROUTE_ID_PREFIX + service);
        route.setUri(URI.create("ws".equals(scheme) ? "lb:ws://" + service : "lb://" + service));
        route.setPredicates(List.of(new PredicateDefinition("Path=" + String.join(",", patterns))));
        route.setFilters(List.of(rateLimiter()));
        return route;
    }

    private FilterDefinition rateLimiter() {
        FilterDefinition filter = new FilterDefinition();
        filter.setName("RequestRateLimiter");
        Map<String, String> args = new LinkedHashMap<>();
        args.put("redis-rate-limiter.replenishRate", String.valueOf(rateLimit.getReplenishRate()));
        args.put("redis-rate-limiter.burstCapacity", String.valueOf(rateLimit.getBurstCapacity()));
        args.put("redis-rate-limiter.requestedTokens", String.valueOf(rateLimit.getRequestedTokens()));
        args.put("key-resolver", "#{@userOrIpKeyResolver}");
        filter.setArgs(args);
        return filter;
    }
}
