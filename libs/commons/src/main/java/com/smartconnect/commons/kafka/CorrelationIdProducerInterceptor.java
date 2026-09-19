package com.smartconnect.commons.kafka;

import com.smartconnect.commons.correlation.CorrelationConstants;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Stamps the correlation id (read from MDC on the calling/producing thread) onto every
 * outbound Kafka record header, so the trace continues from the originating HTTP request
 * onto the {@code user-events} topic. Wire it in via
 * {@code spring.kafka.producer.properties.interceptor.classes} in the producing service's
 * application.yml - no code changes needed in the service itself.
 */
public class CorrelationIdProducerInterceptor implements ProducerInterceptor<Object, Object> {

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        if (record.headers().lastHeader(CorrelationConstants.HEADER_NAME) != null) {
            return record;
        }
        String correlationId = MDC.get(CorrelationConstants.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        record.headers().add(CorrelationConstants.HEADER_NAME, correlationId.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // no-op
    }
}
