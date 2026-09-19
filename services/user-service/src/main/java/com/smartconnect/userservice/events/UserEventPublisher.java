package com.smartconnect.userservice.events;

import com.smartconnect.commons.events.UserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link UserEvent} to the {@code user-events} topic. The correlation id itself is
 * NOT put on the payload - {@code CorrelationIdProducerInterceptor} (wired in via
 * spring.kafka.producer.properties.interceptor.classes) stamps it onto the record header from
 * MDC automatically, so this class only ever deals with the business payload.
 */
@Component
public class UserEventPublisher {

    public static final String USER_EVENTS_TOPIC = "user-events";

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;

    public UserEventPublisher(KafkaTemplate<String, UserEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(UserEvent event) {
        log.info("publishing {} for userId={} to {}", event.eventType(), event.userId(), USER_EVENTS_TOPIC);
        kafkaTemplate.send(USER_EVENTS_TOPIC, event.userId(), event);
    }
}
