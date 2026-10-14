package com.smartconnect.commons.permissions;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The compacted topic carrying each service's {@link com.smartconnect.commons.events.PermissionCatalogEvent}.
 * Declared identically by publishers and by user-service, so whichever starts first creates it
 * with compaction on (broker auto-create would make a plain, non-compacted topic).
 */
public final class PermissionCatalogTopic {

    public static final String NAME = "permission-catalog";

    private PermissionCatalogTopic() {
    }

    public static NewTopic newTopic() {
        return TopicBuilder.name(NAME)
                .partitions(1)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }
}
