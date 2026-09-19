package com.smartconnect.notificationservice.config;

import com.smartconnect.commons.kafka.CorrelationIdRecordInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

/**
 * Overrides Spring Boot's auto-configured listener container factory only to attach
 * {@link CorrelationIdRecordInterceptor} (exposed as a bean by commons'
 * KafkaObservabilityAutoConfiguration) - container factories are inherently service-specific
 * so commons can't wire this itself. Generic params are left as {@code <Object, Object>} (the
 * same type commons' interceptor is written against, and the same type Spring Boot's
 * auto-configured {@link ConsumerFactory} bean uses) - the {@code @KafkaListener} method
 * itself can still declare a strongly-typed {@code UserEvent} parameter; conversion already
 * happened via the configured JsonDeserializer before the interceptor or listener ever run.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            CorrelationIdRecordInterceptor correlationIdRecordInterceptor) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setRecordInterceptor(correlationIdRecordInterceptor);
        return factory;
    }
}
