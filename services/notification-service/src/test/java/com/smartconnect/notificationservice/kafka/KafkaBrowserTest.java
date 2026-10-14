package com.smartconnect.notificationservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EmbeddedKafka(partitions = 2, topics = {KafkaBrowserTest.TOPIC, KafkaBrowserTest.EMPTY})
class KafkaBrowserTest {

    static final String TOPIC = "events.browse";
    static final String EMPTY = "events.empty";
    private static KafkaBrowser browser;

    @BeforeAll
    static void produce(EmbeddedKafkaBroker broker) throws Exception {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of(broker.getBrokersAsString()));
        browser = new KafkaBrowser(properties, new ObjectMapper());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            long t = 1_700_000_000_000L;
            // 7 records over both partitions, one second apart, so time order is unambiguous.
            for (int i = 0; i < 7; i++) {
                String value = i == 6
                        ? "{\"id\":\"e6\",\"type\":\"user.created\",\"source\":\"user-service\",\"correlationId\":\"c-6\"}"
                        : "plain " + i;
                producer.send(new ProducerRecord<>(TOPIC, i % 2, t + i * 1000L, "k" + i, value)).get();
            }
        }
    }

    @AfterAll
    static void close() {
        browser.close();
    }

    @Test
    void topicsCountMessagesAcrossPartitions() {
        List<KafkaBrowser.TopicView> topics = browser.topics();

        assertThat(topics).filteredOn(t -> t.name().equals(TOPIC)).singleElement()
                .satisfies(t -> {
                    assertThat(t.partitions()).isEqualTo(2);
                    assertThat(t.messages()).isEqualTo(7);
                    assertThat(t.deadLetter()).isFalse();
                });
        assertThat(topics).filteredOn(t -> t.name().equals(EMPTY)).singleElement()
                .satisfies(t -> assertThat(t.messages()).isZero());
    }

    @Test
    void newestFirstPagesBackThroughEveryPartition() {
        KafkaBrowser.MessagePage first = browser.messages(TOPIC, null, "newest", 3, null);
        assertThat(first.messages()).extracting(KafkaBrowser.Message::key).containsExactly("k6", "k5", "k4");
        assertThat(first.next()).isNotNull();

        KafkaBrowser.MessagePage second = browser.messages(TOPIC, null, "newest", 3, first.next());
        assertThat(second.messages()).extracting(KafkaBrowser.Message::key).containsExactly("k3", "k2", "k1");

        KafkaBrowser.MessagePage last = browser.messages(TOPIC, null, "newest", 3, second.next());
        assertThat(last.messages()).extracting(KafkaBrowser.Message::key).containsExactly("k0");
        assertThat(last.next()).isNull();
    }

    @Test
    void oldestFirstAndOnePartition() {
        KafkaBrowser.MessagePage oldest = browser.messages(TOPIC, null, "oldest", 2, null);
        assertThat(oldest.messages()).extracting(KafkaBrowser.Message::key).containsExactly("k0", "k1");

        KafkaBrowser.MessagePage partitionOne = browser.messages(TOPIC, 1, "newest", 10, null);
        assertThat(partitionOne.messages()).extracting(KafkaBrowser.Message::key).containsExactly("k5", "k3", "k1");
        assertThat(partitionOne.next()).isNull();
    }

    @Test
    void readsTheEventEnvelopeWhenThePayloadIsOne() {
        KafkaBrowser.Message newest = browser.messages(TOPIC, null, "newest", 1, null).messages().get(0);

        assertThat(newest.envelope()).isNotNull();
        assertThat(newest.envelope().type()).isEqualTo("user.created");
        assertThat(newest.envelope().correlationId()).isEqualTo("c-6");

        KafkaBrowser.Message plain = browser.messages(TOPIC, null, "oldest", 1, null).messages().get(0);
        assertThat(plain.envelope()).isNull();
        assertThat(plain.value()).isEqualTo("plain 0");
    }

    @Test
    void browsingNeverCreatesAConsumerGroup() {
        browser.messages(TOPIC, null, "newest", 5, null);

        assertThat(browser.groups()).isEmpty();
    }

    @Test
    void unknownTopicIsNotFoundAndBadNamesAreRefused() {
        assertThatThrownBy(() -> browser.messages("events.nope", null, "newest", 5, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(() -> browser.messages("bad topic!", null, "newest", 5, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThatThrownBy(() -> KafkaBrowser.parseCursor("zero:x"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    }

    @Test
    void decodesUtf8AsTextAndAnythingElseAsBase64() {
        assertThat(KafkaBrowser.decode("héllo".getBytes(StandardCharsets.UTF_8), 100))
                .isEqualTo(new KafkaBrowser.Decoded("héllo", false, false));
        assertThat(KafkaBrowser.decode(new byte[]{(byte) 0xff, (byte) 0xfe}, 100))
                .isEqualTo(new KafkaBrowser.Decoded("//4=", true, false));
        assertThat(KafkaBrowser.decode("abcdef".getBytes(StandardCharsets.UTF_8), 3))
                .isEqualTo(new KafkaBrowser.Decoded("abc", false, true));
        assertThat(KafkaBrowser.decode(null, 10).text()).isNull();
    }

    @Test
    void headersAreDecodedToo() {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("t", 0, 3L, "k".getBytes(), "v".getBytes());
        record.headers().add("X-Correlation-Id", "abc".getBytes());

        KafkaBrowser.Message message = browser.toMessage(record);

        assertThat(message.headers()).containsExactly(new KafkaBrowser.HeaderView("X-Correlation-Id", "abc", false));
    }
}
