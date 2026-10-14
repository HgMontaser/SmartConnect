package com.smartconnect.apigateway.routing;

import com.smartconnect.commons.discovery.GatewayRouteMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.route.RouteDefinition;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveredRoutesLocatorTest {

    private final DiscoveredRoutesLocator locator = new DiscoveredRoutesLocator(null, new RateLimitProperties());

    @Test
    void buildsLoadBalancedRoutesFromMetadata() {
        List<RouteDefinition> routes = locator.toRoutes(List.of(
                instance("notification-service", Map.of(GatewayRouteMetadata.ROUTES, "/ws/**", GatewayRouteMetadata.SCHEME, "ws")),
                instance("user-service", Map.of(GatewayRouteMetadata.ROUTES, "/api/users/**, /api/roles/**"))));

        assertThat(routes).extracting(r -> r.getUri().toString())
                .containsExactly("lb:ws://notification-service", "lb://user-service");
        assertThat(routes.get(1).getPredicates().get(0).getArgs().values())
                .containsExactly("/api/users/**", "/api/roles/**");
        assertThat(routes.get(1).getFilters().get(0).getArgs())
                .containsEntry("redis-rate-limiter.replenishRate", "10");
    }

    @Test
    void skipsUnroutedServicesAndRefusesClaimedPaths() {
        List<RouteDefinition> routes = locator.toRoutes(List.of(
                instance("api-gateway", Map.of(GatewayRouteMetadata.ROUTES, "")),
                instance("a-service", Map.of(GatewayRouteMetadata.ROUTES, "/api/a/**")),
                instance("b-service", Map.of(GatewayRouteMetadata.ROUTES, "/api/a/**,/api/b/**"))));

        assertThat(routes).extracting(RouteDefinition::getId).containsExactly("discovered-a-service", "discovered-b-service");
        assertThat(routes.get(1).getPredicates().get(0).getArgs().values()).containsExactly("/api/b/**");
    }

    private static ServiceInstance instance(String service, Map<String, String> metadata) {
        return new DefaultServiceInstance(service + "-1", service, "10.0.0.1", 8080, false, metadata);
    }
}
