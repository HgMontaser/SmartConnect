package com.smartconnect.userservice.permission;

import com.smartconnect.commons.events.PermissionCatalogEvent;
import com.smartconnect.commons.permissions.PermissionCatalogTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Applies every business service's published permission catalog. The topic is compacted and
 * read from the beginning by a fresh group, so a newly started user-service (or a rebuilt
 * Keycloak) catches up on every service's latest catalog. A tombstone means the service was
 * removed: all of its permissions become orphaned.
 */
@Component
public class PermissionCatalogListener {

    private final ServicePermissionSync sync;

    public PermissionCatalogListener(ServicePermissionSync sync) {
        this.sync = sync;
    }

    @KafkaListener(topics = PermissionCatalogTopic.NAME, containerFactory = "permissionCatalogListenerFactory")
    public void onCatalog(ConsumerRecord<String, PermissionCatalogEvent> record) {
        PermissionCatalogEvent event = record.value();
        if (event == null) {
            sync.sync(record.key(), Map.of());
            return;
        }
        sync.sync(event.service(), event.permissions());
    }
}
