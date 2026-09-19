package com.smartconnect.userservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import static com.smartconnect.userservice.events.UserEventPublisher.USER_EVENTS_TOPIC;

/**
 * Declares the {@code user-events} topic as a bean so Spring Boot's auto-configured
 * KafkaAdmin creates it (idempotently) at startup, rather than relying on broker
 * auto-create-topics behavior which is implicit and easy to accidentally disable later.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(USER_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
