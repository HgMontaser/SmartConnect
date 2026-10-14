package com.smartconnect.apigateway.routing;

import com.smartconnect.commons.discovery.GatewayRouteMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveredRoutesLocatorTest {

    private final MutableClock clock = new MutableClock();
    private final DiscoveredRoutesLocator locator = new DiscoveredRoutesLocator(null, new RateLimitProperties(), clock);

    @Test
    void buildsLoadBalancedRoutesWithProtectionFromMetadata() {
        List<RouteDefinition> routes = locator.toRoutes(List.of(
                instance("notification-service", Map.of(GatewayRouteMetadata.ROUTES, "/ws/**", GatewayRouteMetadata.SCHEME, "ws")),
                instance("user-service", Map.of(GatewayRouteMetadata.ROUTES, "/api/users/**, /api/roles/**"))));

        assertThat(routes).extracting(r -> r.getUri().toString())
                .containsExactly("lb:ws://notification-service", "lb://user-service");
        assertThat(routes.get(1).getPredicates().get(0).getArgs().values())
                .containsExactly("/api/users/**", "/api/roles/**");
        assertThat(routes.get(1).getFilters()).extracting(FilterDefinition::getName)
                .containsExactly("RequestRateLimiter", "CircuitBreaker", "Retry");
        assertThat(routes.get(1).getFilters().get(1).getArgs()).containsEntry("name", "user-service");
        assertThat(routes.get(1).getFilters().get(2).getArgs()).containsEntry("methods", "GET");
        // WebSockets: rate limiting only.
        assertThat(routes.get(0).getFilters()).extracting(FilterDefinition::getName).containsExactly("RequestRateLimiter");
    }

    @Test
    void splitsMixedHttpAndWebSocketPatternsAndRefusesReservedPaths() {
        List<RouteDefinition> routes = locator.toRoutes(List.of(
                instance("notification-service", Map.of(GatewayRouteMetadata.ROUTES, "/api/events/**, ws:/ws/**, /api/platform/**"))));

        assertThat(routes).extracting(RouteDefinition::getId)
                .containsExactly("discovered-notification-service", "discovered-notification-service-ws");
        assertThat(routes.get(0).getUri().toString()).isEqualTo("lb://notification-service");
        assertThat(routes.get(0).getPredicates().get(0).getArgs().values()).containsExactly("/api/events/**");
        assertThat(routes.get(1).getUri().toString()).isEqualTo("lb:ws://notification-service");
        assertThat(routes.get(1).getPredicates().get(0).getArgs().values()).containsExactly("/ws/**");
    }

    @Test
    void appliesAServiceTimeout() {
        List<RouteDefinition> routes = locator.toRoutes(List.of(
                instance("slow-service", Map.of(GatewayRouteMetadata.ROUTES, "/api/slow/**", GatewayRouteMetadata.TIMEOUT, "30s")),
                instance("bad-service", Map.of(GatewayRouteMetadata.ROUTES, "/api/bad/**", GatewayRouteMetadata.TIMEOUT, "soon"))));

        assertThat(routes.get(0).getMetadata()).containsEntry("response-timeout", 30_000L);
        assertThat(routes.get(1).getMetadata()).doesNotContainKey("response-timeout");
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

    @Test
    void keepsAVanishedServicesRouteForTheGracePeriod() {
        locator.toRoutes(List.of(instance("demo-service", Map.of(GatewayRouteMetadata.ROUTES, "/api/demo/**"))));

        clock.advance(Duration.ofMinutes(5));
        assertThat(locator.toRoutes(List.of())).extracting(RouteDefinition::getId).containsExactly("discovered-demo-service");

        clock.advance(DiscoveredRoutesLocator.ROUTE_GRACE);
        assertThat(locator.toRoutes(List.of())).isEmpty();
    }

    @Test
    void aRememberedRouteNeverShadowsALiveOwnerOfTheSamePath() {
        locator.toRoutes(List.of(instance("old-service", Map.of(GatewayRouteMetadata.ROUTES, "/api/x/**"))));

        List<RouteDefinition> routes = locator.toRoutes(List.of(instance("new-service", Map.of(GatewayRouteMetadata.ROUTES, "/api/x/**"))));

        assertThat(routes).extracting(RouteDefinition::getId).containsExactly("discovered-new-service");
    }

    private static ServiceInstance instance(String service, Map<String, String> metadata) {
        return new DefaultServiceInstance(service + "-1", service, "10.0.0.1", 8080, false, metadata);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
