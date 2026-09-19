package com.smartconnect.commons.kafka;

import com.smartconnect.commons.correlation.CorrelationConstants;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Reads the correlation id off a consumed record's Kafka headers and puts it back into MDC
 * before the {@code @KafkaListener} method runs, so the trace continues into this service's
 * logs too. Register it on the listener container factory:
 * {@code factory.setRecordInterceptor(correlationIdRecordInterceptor)}.
 *
 * <p>Uses Spring Kafka's per-record {@link RecordInterceptor} (not a raw
 * {@code ConsumerInterceptor}) specifically because it runs once per record on the listener
 * thread right before invocation, with {@link #success}/{@link #failure} hooks to clear MDC
 * afterwards - a plain {@code ConsumerInterceptor.onConsume} operates on a whole batch and
 * would let ids from different records bleed into each other.
 */
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    @Nullable
    public ConsumerRecord<Object, Object> intercept(
            @NonNull ConsumerRecord<Object, Object> record, @NonNull Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(CorrelationConstants.HEADER_NAME);
        String correlationId = header != null
                ? new String(header.value(), StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();
        MDC.put(CorrelationConstants.MDC_KEY, correlationId);
        return record;
    }

    @Override
    public void success(@NonNull ConsumerRecord<Object, Object> record, @NonNull Consumer<Object, Object> consumer) {
        MDC.remove(CorrelationConstants.MDC_KEY);
    }

    @Override
    public void failure(
            @NonNull ConsumerRecord<Object, Object> record, @NonNull Exception exception, @NonNull Consumer<Object, Object> consumer) {
        MDC.remove(CorrelationConstants.MDC_KEY);
    }
}
