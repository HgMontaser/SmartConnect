package com.smartconnect.apigateway.platform;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.catalog.CatalogServicesRequest;
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.model.Check;
import com.ecwid.consul.v1.health.model.HealthService;
import com.smartconnect.apigateway.routing.DiscoveredRoutesLocator;
import com.smartconnect.commons.discovery.GatewayRouteMetadata;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The platform as the gateway sees it, for the backoffice "Gateway health" page: every service
 * registered in Consul with all its instances (healthy or not), the routes the gateway built
 * for it, and its circuit breaker. Served by the gateway itself under the reserved
 * {@code /api/platform} prefix.
 */
@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private final ConsulClient consul;
    private final DiscoveredRoutesLocator routes;
    private final CircuitBreakerRegistry breakers;
    private final PlatformMetrics metrics;
    /** The gateway's own Consul ACL token: with deny-by-default, an anonymous query sees no services. */
    private final String consulToken;

    public PlatformController(ConsulClient consul, DiscoveredRoutesLocator routes, CircuitBreakerRegistry breakers,
                              PlatformMetrics metrics, @Value("${spring.cloud.consul.token:}") String consulToken) {
        this.consul = consul;
        this.routes = routes;
        this.breakers = breakers;
        this.metrics = metrics;
        this.consulToken = consulToken.isBlank() ? null : consulToken;
    }

    /** Requests, 5xx rate and p95 latency per service over {@code range} (15m, 1h, 6h, 24h). */
    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('platform:read')")
    public Mono<PlatformMetrics.MetricsView> metrics(@RequestParam(defaultValue = "1h") String range) {
        return metrics.fetch(PlatformMetrics.Range.of(range));
    }

    @GetMapping("/services")
    @PreAuthorize("hasAuthority('platform:read')")
    public Mono<List<ServiceView>> services() {
        return routes.getRouteDefinitions().collectList()
                .flatMap(routeList -> Mono.fromCallable(() -> snapshot(routeList)).subscribeOn(Schedulers.boundedElastic()));
    }

    private List<ServiceView> snapshot(List<RouteDefinition> routeList) {
        List<ServiceView> services = new ArrayList<>();
        for (String name : consul.getCatalogServices(CatalogServicesRequest.newBuilder().setToken(consulToken).build()).getValue().keySet()) {
            if ("consul".equals(name)) {
                continue;
            }
            List<HealthService> entries = consul.getHealthServices(name, HealthServicesRequest.newBuilder().setToken(consulToken).build()).getValue();
            List<InstanceView> instances = entries.stream().map(PlatformController::instance).toList();
            Map<String, String> meta = entries.isEmpty() ? Map.of() : entries.get(0).getService().getMeta();
            List<RouteView> serviceRoutes = routeList.stream()
                    .filter(r -> name.equals(targetService(r)))
                    .map(r -> new RouteView(r.getId(), r.getUri().toString().startsWith("lb:ws://") ? "ws" : "http",
                            List.copyOf(r.getPredicates().get(0).getArgs().values())))
                    .toList();
            String timeout = meta == null ? null : meta.get(GatewayRouteMetadata.TIMEOUT);
            services.add(new ServiceView(name, instances, serviceRoutes, circuit(name),
                    timeout == null || timeout.isBlank() ? null : timeout));
        }
        services.sort(Comparator.comparing(ServiceView::name));
        return services;
    }

    /** lb://user-service and lb:ws://notification-service -> the service name. */
    private static String targetService(RouteDefinition route) {
        String uri = route.getUri().toString();
        return uri.substring(uri.indexOf("://") + 3);
    }

    private String circuit(String service) {
        return breakers.getAllCircuitBreakers().stream()
                .filter(cb -> cb.getName().equals(service))
                .findFirst()
                .map(CircuitBreaker::getState)
                .map(Enum::name)
                .orElse(null);
    }

    private static InstanceView instance(HealthService entry) {
        String status = entry.getChecks().stream()
                .filter(c -> !"serfHealth".equals(c.getCheckId()))
                .map(Check::getStatus)
                .map(Enum::name)
                .reduce((a, b) -> "PASSING".equals(a) ? b : a)
                .orElse("PASSING");
        return new InstanceView(entry.getService().getId(), entry.getService().getAddress(), entry.getService().getPort(), status);
    }

    public record ServiceView(String name, List<InstanceView> instances, List<RouteView> routes, String circuit, String timeout) {
    }

    public record InstanceView(String id, String address, int port, String status) {
    }

    public record RouteView(String id, String scheme, List<String> paths) {
    }
}
