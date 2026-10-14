package com.smartconnect.commons.messaging;

import com.smartconnect.commons.kafka.CorrelationIdRecordInterceptor;
import com.smartconnect.commons.kafka.ObservedKafkaTemplates;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wires {@link EventPublisher} and the {@link EventListeners#FACTORY} listener factory into
 * every service that has spring-kafka. Uses its own producers/consumers built from the
 * service's {@code spring.kafka.*} connection settings, so it doesn't depend on how the
 * service configures Kafka for anything else.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class MessagingAutoConfiguration {

    static final int MAX_RETRIES = 3;

    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher(KafkaProperties kafka, @Value("${spring.application.name}") String service,
                                         ApplicationContext context) {
        return new EventPublisher(envelopeTemplate(kafka, context), service);
    }

    @Bean(EventListeners.FACTORY)
    @ConditionalOnMissingBean(name = EventListeners.FACTORY)
    @SuppressWarnings("unchecked")
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope> smartconnectEventListenerFactory(
            KafkaProperties kafka, @Value("${spring.application.name}") String service,
            ObjectProvider<CorrelationIdRecordInterceptor> correlation, ApplicationContext context) {
        Map<String, Object> config = kafka.buildConsumerProperties(null);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, service);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        DefaultKafkaConsumerFactory<String, EventEnvelope> consumers = new DefaultKafkaConsumerFactory<>(config,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>(EventEnvelope.class, false)));

        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumers);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(errorHandler(kafka, service, context));
        correlation.ifAvailable(interceptor ->
                factory.setRecordInterceptor((RecordInterceptor<String, EventEnvelope>) (RecordInterceptor<?, ?>) interceptor));
        return factory;
    }

    /** Retries with backoff (0.5s, 1s, 2s), then parks the record on this service's dead-letter topic. */
    private DefaultErrorHandler errorHandler(KafkaProperties kafka, String service, ApplicationContext context) {
        // Undecodable records arrive as raw bytes, decoded ones as envelopes - one template each.
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate(kafka, context));
        templates.put(EventEnvelope.class, envelopeTemplate(kafka, context));
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates,
                (record, ex) -> new TopicPartition(EventTopics.deadLetter(record.topic(), service), -1));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    /** Observed, so the trace context goes out in the record headers (see {@link ObservedKafkaTemplates}). */
    private static KafkaTemplate<String, EventEnvelope> envelopeTemplate(KafkaProperties kafka, ApplicationContext context) {
        Map<String, Object> config = kafka.buildProducerProperties(null);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return ObservedKafkaTemplates.observed(new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config)), context);
    }

    private static KafkaTemplate<String, byte[]> bytesTemplate(KafkaProperties kafka, ApplicationContext context) {
        Map<String, Object> config = kafka.buildProducerProperties(null);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        return ObservedKafkaTemplates.observed(new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config)), context);
    }
}
