package com.smartconnect.commons.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Exposes {@link CorrelationIdRecordInterceptor} as a bean for any service with spring-kafka
 * on the classpath. A consuming service still needs to attach it to its own
 * {@code ConcurrentKafkaListenerContainerFactory} (container factories are always
 * service-specific - topic, group id, concurrency - so commons can't build that factory for
 * you), but the interceptor logic itself lives here once.
 */
@AutoConfiguration
@ConditionalOnClass(RecordInterceptor.class)
public class KafkaObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdRecordInterceptor correlationIdRecordInterceptor() {
        return new CorrelationIdRecordInterceptor();
    }
}
