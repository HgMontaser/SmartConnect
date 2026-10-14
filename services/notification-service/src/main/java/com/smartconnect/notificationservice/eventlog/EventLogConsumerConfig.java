package com.smartconnect.notificationservice.eventlog;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Consumer for the event log: plain strings, its own group per instance (so it never takes
 * events away from real consumers and replays recent history on start), and a short metadata
 * refresh so topics created later - a new service's domain, a new dead-letter topic - show up
 * within seconds.
 */
@Configuration
public class EventLogConsumerConfig {

    static final String FACTORY = "eventLogListenerFactory";

    @Bean(FACTORY)
    public ConcurrentKafkaListenerContainerFactory<String, String> eventLogListenerFactory(KafkaProperties kafka) {
        Map<String, Object> config = kafka.buildConsumerProperties(null);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service-event-log-" + UUID.randomUUID().toString().substring(0, 8));
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 10_000);
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer()));
        return factory;
    }
}
