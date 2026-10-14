package com.smartconnect.userservice.config;

import com.smartconnect.commons.events.PermissionCatalogEvent;
import com.smartconnect.commons.kafka.CorrelationIdRecordInterceptor;
import com.smartconnect.commons.permissions.PermissionCatalogTopic;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * user-service's only Kafka consumer: the permission catalog. Built separately from the
 * (producer-only) Boot defaults so its group, offset reset and JSON target type stay local.
 * A malformed record is logged and skipped instead of blocking the partition.
 */
@Configuration
public class PermissionCatalogConsumerConfig {

    @Bean
    public NewTopic permissionCatalogTopic() {
        return PermissionCatalogTopic.newTopic();
    }

    @Bean
    @SuppressWarnings("unchecked")
    public ConcurrentKafkaListenerContainerFactory<String, PermissionCatalogEvent> permissionCatalogListenerFactory(
            KafkaProperties kafkaProperties, CorrelationIdRecordInterceptor correlationIdRecordInterceptor) {
        Map<String, Object> config = kafkaProperties.buildConsumerProperties(null);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "user-service-permission-catalog");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<PermissionCatalogEvent> json = new JsonDeserializer<>(PermissionCatalogEvent.class, false);
        DefaultKafkaConsumerFactory<String, PermissionCatalogEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(
                config, new StringDeserializer(), new ErrorHandlingDeserializer<>(json));

        ConcurrentKafkaListenerContainerFactory<String, PermissionCatalogEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setRecordInterceptor((RecordInterceptor<String, PermissionCatalogEvent>) (RecordInterceptor<?, ?>) correlationIdRecordInterceptor);
        return factory;
    }
}
