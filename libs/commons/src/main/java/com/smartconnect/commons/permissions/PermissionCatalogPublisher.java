package com.smartconnect.commons.permissions;

import com.smartconnect.commons.events.PermissionCatalogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Publishes this service's full permission catalog to {@link PermissionCatalogTopic#NAME},
 * keyed by service name. Runs once the application is ready (after the startup check), and
 * again whenever the service calls {@link #publish()} - e.g. right after it changes its own
 * permissions table. Sending is asynchronous so a slow or absent broker never blocks startup.
 */
public class PermissionCatalogPublisher {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalogPublisher.class);

    private final String service;
    private final PermissionCatalogProvider provider;
    private final KafkaTemplate<String, PermissionCatalogEvent> kafkaTemplate;
    private final DeclaredPermissionsVerifier verifier;
    private final boolean verify;

    public PermissionCatalogPublisher(String service, PermissionCatalogProvider provider,
                                      KafkaTemplate<String, PermissionCatalogEvent> kafkaTemplate,
                                      DeclaredPermissionsVerifier verifier, boolean verify) {
        if (!PermissionNames.isValidService(service)) {
            throw new IllegalStateException("smartconnect.permissions.service must match "
                    + PermissionNames.SERVICE.pattern() + ": " + service);
        }
        this.service = service;
        this.provider = provider;
        this.kafkaTemplate = kafkaTemplate;
        this.verifier = verifier;
        this.verify = verify;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (verify) {
            verifier.verify(catalog().keySet());
        }
        publish();
    }

    public void publish() {
        Map<String, String> permissions = catalog();
        PermissionCatalogEvent event = new PermissionCatalogEvent(service, permissions, Instant.now());
        kafkaTemplate.send(PermissionCatalogTopic.NAME, service, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("failed to publish permission catalog for {}", service, ex);
            } else {
                log.info("published permission catalog for {}: {} permissions", service, permissions.size());
            }
        });
    }

    private Map<String, String> catalog() {
        Map<String, String> permissions = new TreeMap<>();
        provider.permissions().forEach((name, description) -> {
            if (!PermissionNames.isValid(name)) {
                throw new IllegalStateException("Invalid permission name '" + name + "' in " + service
                        + " catalog - expected resource:action");
            }
            permissions.put(name, description);
        });
        return new LinkedHashMap<>(permissions);
    }
}
