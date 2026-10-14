package com.smartconnect.commons.messaging;

import com.smartconnect.commons.correlation.CorrelationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes events in the standard {@link EventEnvelope}:
 * <pre>
 * events.publish("billing.invoice-paid", invoice.id(), new InvoicePaid(...));
 * </pre>
 * The topic follows from the type ({@link EventTopics}), the key keeps one entity's events in
 * order, and the trace and correlation id travel with the record automatically. Sending is
 * asynchronous; failures are logged and surface through the returned future.
 */
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private static final int DEFAULT_VERSION = 1;

    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;
    private final String source;

    public EventPublisher(KafkaTemplate<String, EventEnvelope> kafkaTemplate, String source) {
        this.kafkaTemplate = kafkaTemplate;
        this.source = source;
    }

    public CompletableFuture<SendResult<String, EventEnvelope>> publish(String type, String key, Object payload) {
        return publish(type, DEFAULT_VERSION, key, payload);
    }

    public CompletableFuture<SendResult<String, EventEnvelope>> publish(String type, int version, String key, Object payload) {
        String topic = EventTopics.forType(type);
        EventEnvelope event = new EventEnvelope(UUID.randomUUID(), type, version, source, Instant.now(),
                MDC.get(CorrelationConstants.MDC_KEY), EventEnvelope.toPayload(payload));
        return kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("failed to publish {} (id={}) to {}", type, event.id(), topic, ex);
            } else {
                log.info("published {} (id={}) to {}", type, event.id(), topic);
            }
        });
    }
}
