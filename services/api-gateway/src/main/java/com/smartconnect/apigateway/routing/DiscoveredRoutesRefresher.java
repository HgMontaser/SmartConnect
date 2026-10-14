package com.smartconnect.apigateway.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Consul's catalog watch only fires when services are added or removed, not when an
 * instance's health changes - and services register as critical until their first heartbeat,
 * so the routes built at that moment would miss them until some unrelated catalog change.
 * This re-evaluates the discovered routes every few seconds and refreshes the gateway only
 * when they actually differ (a service became healthy, went away, or changed its routes).
 */
@Configuration
@EnableScheduling
public class DiscoveredRoutesRefresher {

    private static final Logger log = LoggerFactory.getLogger(DiscoveredRoutesRefresher.class);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(5);

    private final DiscoveredRoutesLocator locator;
    private final ApplicationEventPublisher events;
    private volatile List<String> lastRoutes;

    public DiscoveredRoutesRefresher(DiscoveredRoutesLocator locator, ApplicationEventPublisher events) {
        this.locator = locator;
        this.events = events;
    }

    @Scheduled(initialDelay = 2000, fixedDelay = 3000)
    public void refreshIfChanged() {
        List<String> current;
        try {
            current = locator.getRouteDefinitions().map(DiscoveredRoutesRefresher::describe).collectList().block(LOOKUP_TIMEOUT);
        } catch (RuntimeException e) {
            log.warn("could not read routes from Consul: {}", e.getMessage());
            return;
        }
        if (!Objects.equals(current, lastRoutes)) {
            log.info("gateway routes changed: {}", current);
            lastRoutes = current;
            events.publishEvent(new RefreshRoutesEvent(this));
        }
    }

    private static String describe(RouteDefinition route) {
        return route.getId() + " " + route.getUri() + " " + route.getPredicates().get(0).getArgs().values()
                + " " + route.getFilters().get(0).getArgs();
    }
}
