package com.smartconnect.commons.it;

import com.smartconnect.commons.messaging.EventEnvelope;
import com.smartconnect.commons.messaging.EventListeners;
import com.smartconnect.commons.messaging.EventPublisher;
import com.smartconnect.commons.messaging.MessagingAutoConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The event contract against a real broker: publish/consume, the trace carrying over from
 * publisher to listener, retries then dead-letter for a throwing listener, and
 * straight-to-dead-letter for an undecodable record.
 */
@Testcontainers
class MessagingIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    /** Each test gets its own event domain (topic) and consumer, so tests can't see each other's records. */
    private final String domain = "it" + UUID.randomUUID().toString().substring(0, 8);
    private final String type = domain + ".thing-happened";
    private final String topic = "events." + domain;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class, MessagingAutoConfiguration.class,
                        // The services' tracing setup (minus the OTLP exporter).
                        ObservationAutoConfiguration.class, MicrometerTracingAutoConfiguration.class,
                        OpenTelemetryAutoConfiguration.class,
                        org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration.class))
                .withUserConfiguration(Listener.class)
                .withPropertyValues(
                        "management.tracing.sampling.probability=1.0",
                        "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                        "spring.application.name=it-consumer-" + UUID.randomUUID().toString().substring(0, 8),
                        "it.topic=" + topic);
    }

    @Test
    void publishedEventsReachListenersInTheStandardEnvelope() {
        runner().run(context -> {
            Listener listener = context.getBean(Listener.class);
            context.getBean(EventPublisher.class).publish(type, "k1", Map.of("text", "hello")).get();

            await().atMost(Duration.ofSeconds(30)).until(() -> !listener.received.isEmpty());
            EventEnvelope event = listener.received.get(0);
            assertThat(event.type()).isEqualTo(type);
            assertThat(event.source()).startsWith("it-consumer-");
            assertThat(event.id()).isNotNull();
            assertThat(event.payloadAs(Map.class)).containsEntry("text", "hello");
        });
    }

    @Test
    void theListenerContinuesThePublishersTrace() {
        runner().run(context -> {
            Listener listener = context.getBean(Listener.class);
            Tracer tracer = context.getBean(Tracer.class);
            Span request = tracer.nextSpan().name("request").start();
            try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
                context.getBean(EventPublisher.class).publish(type, "k3", Map.of("text", "traced")).get();
            } finally {
                request.end();
            }

            await().atMost(Duration.ofSeconds(30)).until(() -> !listener.received.isEmpty());
            String traceId = request.context().traceId();
            assertThat(listener.traceparents.get(0)).contains(traceId);
            assertThat(listener.traceIds.get(0)).isEqualTo(traceId);
        });
    }

    @Test
    void aThrowingListenerIsRetriedThenTheRecordIsDeadLettered() {
        runner().run(context -> {
            Listener listener = context.getBean(Listener.class);
            String service = context.getEnvironment().getProperty("spring.application.name");
            context.getBean(EventPublisher.class).publish(type, "k2", Map.of("text", "fail please")).get();

            List<ConsumerRecord<String, String>> dead = readAll(topic + "." + service + ".dlt", 1);
            assertThat(dead).hasSize(1);
            assertThat(header(dead.get(0), KafkaHeaders.DLT_EXCEPTION_MESSAGE)).contains("boom");
            assertThat(listener.failures.size()).isEqualTo(1 + 3); // first attempt + 3 retries
        });
    }

    @Test
    void anUndecodableRecordGoesStraightToTheDeadLetterTopic() {
        runner().run(context -> {
            Listener listener = context.getBean(Listener.class);
            String service = context.getEnvironment().getProperty("spring.application.name");
            // Let the consumer join before sending, then send garbage.
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                context.getBean(EventPublisher.class).publish(type, "warmup", Map.of("text", "warmup")).get();
                return !listener.received.isEmpty();
            });
            try (KafkaProducer<String, String> producer = rawProducer()) {
                producer.send(new ProducerRecord<>(topic, "poison", "{not json")).get();
            }

            List<ConsumerRecord<String, String>> dead = readAll(topic + "." + service + ".dlt", 1);
            assertThat(dead).extracting(ConsumerRecord::key).contains("poison");
            assertThat(listener.failures).isEmpty();
        });
    }

    private static List<ConsumerRecord<String, String>> readAll(String topic, int expected) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(60)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
                return records.size() >= expected;
            });
        }
        return records;
    }

    private static KafkaProducer<String, String> rawProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        return new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    @EnableKafka
    static class Listener {
        final List<EventEnvelope> received = new CopyOnWriteArrayList<>();
        final List<EventEnvelope> failures = new CopyOnWriteArrayList<>();
        /** The record's W3C trace header and the trace the listener ran in, per received event. */
        final List<String> traceparents = new CopyOnWriteArrayList<>();
        final List<String> traceIds = new CopyOnWriteArrayList<>();
        private final ObjectProvider<Tracer> tracer;

        Listener(ObjectProvider<Tracer> tracer) {
            this.tracer = tracer;
        }

        @KafkaListener(topics = "${it.topic}", containerFactory = EventListeners.FACTORY)
        void on(EventEnvelope event, @Header(name = "traceparent", required = false) String traceparent) {
            if (event.payloadAs(Map.class).get("text").toString().contains("fail")) {
                failures.add(event);
                throw new IllegalStateException("boom");
            }
            Span current = tracer.getObject().currentSpan();
            traceparents.add(String.valueOf(traceparent));
            traceIds.add(current == null ? "none" : current.context().traceId());
            received.add(event);
        }
    }
}
