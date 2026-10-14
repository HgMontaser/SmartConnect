package com.smartconnect.notificationservice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartconnect.commons.messaging.EventTopics;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Kafka as the backoffice's Events page sees it, read on demand (nothing kept in memory): every
 * topic with its partitions and message counts, any topic's messages - newest or oldest first,
 * pageable - with key, headers and payload, and every consumer group with its lag. Messages are
 * read with a throwaway consumer that assigns partitions directly, so browsing never joins a
 * group or commits an offset.
 */
@Component
public class KafkaBrowser {

    static final int MAX_LIMIT = 200;
    /** Payloads beyond this are cut, so one huge record can't bloat the page. */
    static final int MAX_VALUE_CHARS = 64 * 1024;
    private static final Pattern TOPIC = Pattern.compile("[A-Za-z0-9._-]{1,249}");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Admin admin;
    private final Map<String, Object> consumerConfig;
    private final ObjectMapper json;

    public KafkaBrowser(KafkaProperties kafka, ObjectMapper json) {
        this.admin = Admin.create(kafka.buildAdminProperties(null));
        Map<String, Object> config = new HashMap<>(kafka.buildConsumerProperties(null));
        config.remove(ConsumerConfig.GROUP_ID_CONFIG);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_LIMIT);
        this.consumerConfig = config;
        this.json = json;
    }

    @PreDestroy
    void close() {
        admin.close(Duration.ofSeconds(2));
    }

    // ---------------------------------------------------------------- topics

    public List<TopicView> topics() {
        return call(() -> {
            Set<String> names = admin.listTopics().names().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (names.isEmpty()) {
                return List.of();
            }
            Map<String, TopicDescription> descriptions = admin.describeTopics(names).allTopicNames().get();
            List<TopicPartition> partitions = descriptions.values().stream()
                    .flatMap(d -> d.partitions().stream().map(p -> new TopicPartition(d.name(), p.partition())))
                    .toList();
            Map<TopicPartition, Long> earliest = offsets(partitions, OffsetSpec.earliest());
            Map<TopicPartition, Long> latest = offsets(partitions, OffsetSpec.latest());
            Map<String, String> retention = retention(names);
            Map<String, List<String>> readers = readersByTopic();

            return names.stream().sorted().map(name -> {
                TopicDescription d = descriptions.get(name);
                long messages = d.partitions().stream()
                        .mapToLong(p -> {
                            TopicPartition tp = new TopicPartition(name, p.partition());
                            return latest.getOrDefault(tp, 0L) - earliest.getOrDefault(tp, 0L);
                        })
                        .sum();
                return new TopicView(name, d.partitions().size(), messages, EventTopics.isDeadLetter(name),
                        d.isInternal() || name.startsWith("__"), retention.get(name),
                        readers.getOrDefault(name, List.of()));
            }).toList();
        });
    }

    // -------------------------------------------------------------- messages

    /**
     * Messages of one topic. {@code newest}: the latest {@code limit} across the chosen
     * partitions, newest first; {@code before} ("partition:offset,...", from a previous page's
     * {@code older}) continues further back. {@code oldest}: from the beginning, oldest first,
     * {@code after} continuing forward.
     */
    public MessagePage messages(String topic, Integer partition, String order, int limit, String cursor) {
        requireTopic(topic);
        int max = Math.max(1, Math.min(limit, MAX_LIMIT));
        boolean newest = !"oldest".equals(order);
        return call(() -> {
            TopicDescription description;
            try {
                description = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no topic " + topic);
                }
                throw e;
            }
            List<TopicPartition> partitions = description.partitions().stream()
                    .filter(p -> partition == null || p.partition() == partition)
                    .map(p -> new TopicPartition(topic, p.partition()))
                    .toList();
            if (partitions.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no partition " + partition + " in " + topic);
            }
            Map<TopicPartition, Long> earliest = offsets(partitions, OffsetSpec.earliest());
            Map<TopicPartition, Long> latest = offsets(partitions, OffsetSpec.latest());
            Map<Integer, Long> position = parseCursor(cursor);

            // Per partition, the range [from, to) to read: at most `max` records each, then merged.
            Map<TopicPartition, long[]> ranges = new LinkedHashMap<>();
            for (TopicPartition tp : partitions) {
                // A continued page leaves a partition out of its cursor once that one is exhausted.
                if (cursor != null && !cursor.isBlank() && !position.containsKey(tp.partition())) {
                    continue;
                }
                long begin = earliest.get(tp);
                long end = latest.get(tp);
                long from;
                long to;
                if (newest) {
                    to = Math.max(begin, Math.min(end, position.getOrDefault(tp.partition(), end)));
                    from = Math.max(begin, to - max);
                } else {
                    from = Math.min(end, Math.max(begin, position.getOrDefault(tp.partition(), begin)));
                    to = Math.min(end, from + max);
                }
                if (to > from) {
                    ranges.put(tp, new long[]{from, to});
                }
            }

            List<Message> read = read(ranges);
            Comparator<Message> byTime = Comparator.comparing(Message::timestamp).thenComparingLong(Message::offset);
            List<Message> page = read.stream().sorted(newest ? byTime.reversed() : byTime).limit(max).toList();

            // Where the next page starts, per partition: just past what this page showed.
            Map<Integer, Long> next = new LinkedHashMap<>();
            for (TopicPartition tp : ranges.keySet()) {
                List<Message> shown = page.stream().filter(m -> m.partition() == tp.partition()).toList();
                long[] range = ranges.get(tp);
                if (newest) {
                    long lowest = shown.stream().mapToLong(Message::offset).min().orElse(range[1]);
                    if (lowest > earliest.get(tp)) {
                        next.put(tp.partition(), lowest);
                    }
                } else {
                    long highest = shown.stream().mapToLong(Message::offset).max().orElse(range[0] - 1);
                    if (highest + 1 < latest.get(tp)) {
                        next.put(tp.partition(), highest + 1);
                    }
                }
            }
            String nextCursor = next.isEmpty() ? null : next.entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(","));
            return new MessagePage(topic, newest ? "newest" : "oldest", page, nextCursor);
        });
    }

    private List<Message> read(Map<TopicPartition, long[]> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>();
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(consumerConfig)) {
            consumer.assign(ranges.keySet());
            ranges.forEach((tp, range) -> consumer.seek(tp, range[0]));
            Map<TopicPartition, long[]> remaining = new HashMap<>(ranges);
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (!remaining.isEmpty() && System.nanoTime() < deadline) {
                for (ConsumerRecord<byte[], byte[]> record : consumer.poll(Duration.ofMillis(300))) {
                    TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                    long[] range = remaining.get(tp);
                    if (range != null && record.offset() < range[1]) {
                        messages.add(toMessage(record));
                    }
                }
                // Done with a partition once its position reached the end of its range (offsets can
                // have gaps - transaction markers, compaction - so count positions, not records).
                remaining.entrySet().removeIf(e -> consumer.position(e.getKey()) >= e.getValue()[1]);
                consumer.pause(ranges.keySet().stream().filter(tp -> !remaining.containsKey(tp)).toList());
            }
        }
        return messages;
    }

    Message toMessage(ConsumerRecord<byte[], byte[]> record) {
        Decoded value = decode(record.value(), MAX_VALUE_CHARS);
        List<HeaderView> headers = new ArrayList<>();
        for (Header header : record.headers()) {
            Decoded h = decode(header.value(), 2048);
            headers.add(new HeaderView(header.key(), h.text(), h.binary()));
        }
        return new Message(record.partition(), record.offset(), Instant.ofEpochMilli(record.timestamp()),
                decode(record.key(), 1024).text(), headers, value.text(), value.binary(), value.truncated(),
                record.serializedValueSize(), envelope(value));
    }

    /** The standard event envelope's fields, when the payload is one (see commons EventEnvelope). */
    private Envelope envelope(Decoded value) {
        if (value.text() == null || value.binary() || value.truncated()) {
            return null;
        }
        try {
            JsonNode node = json.readTree(value.text());
            if (node == null || !node.isObject() || !node.hasNonNull("type")) {
                return null;
            }
            return new Envelope(text(node, "id"), text(node, "type"), text(node, "version"), text(node, "source"),
                    text(node, "occurredAt"), text(node, "correlationId"));
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------- consumers

    public List<GroupView> groups() {
        return call(() -> {
            List<String> ids = admin.listConsumerGroups().all().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .stream().map(ConsumerGroupListing::groupId).sorted().toList();
            if (ids.isEmpty()) {
                return List.of();
            }
            Map<String, ConsumerGroupDescription> descriptions = admin.describeConsumerGroups(ids).all().get();
            Map<String, Map<TopicPartition, OffsetAndMetadata>> committed = new HashMap<>();
            for (String id : ids) {
                committed.put(id, admin.listConsumerGroupOffsets(id).partitionsToOffsetAndMetadata().get());
            }
            List<TopicPartition> all = committed.values().stream().flatMap(m -> m.keySet().stream()).distinct().toList();
            Map<TopicPartition, Long> latest = offsets(all, OffsetSpec.latest());

            List<GroupView> groups = new ArrayList<>();
            for (String id : ids) {
                ConsumerGroupDescription d = descriptions.get(id);
                Map<String, long[]> perTopic = new LinkedHashMap<>(); // topic -> {lag, partitions}
                committed.get(id).forEach((tp, offset) -> {
                    if (offset == null) {
                        return;
                    }
                    long lag = Math.max(0, latest.getOrDefault(tp, offset.offset()) - offset.offset());
                    long[] t = perTopic.computeIfAbsent(tp.topic(), k -> new long[2]);
                    t[0] += lag;
                    t[1]++;
                });
                List<GroupTopic> topics = perTopic.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> new GroupTopic(e.getKey(), e.getValue()[0], (int) e.getValue()[1]))
                        .toList();
                groups.add(new GroupView(id, d.state().name(), d.members().size(),
                        topics.stream().mapToLong(GroupTopic::lag).sum(), topics));
            }
            return groups;
        });
    }

    /** topic -> the consumer groups with committed offsets on it. */
    private Map<String, List<String>> readersByTopic() throws Exception {
        Map<String, List<String>> readers = new HashMap<>();
        for (ConsumerGroupListing group : admin.listConsumerGroups().all().get()) {
            admin.listConsumerGroupOffsets(group.groupId()).partitionsToOffsetAndMetadata().get().keySet().stream()
                    .map(TopicPartition::topic).distinct()
                    .forEach(topic -> readers.computeIfAbsent(topic, k -> new ArrayList<>()).add(group.groupId()));
        }
        readers.values().forEach(list -> list.sort(String::compareTo));
        return readers;
    }

    // --------------------------------------------------------------- helpers

    private Map<TopicPartition, Long> offsets(List<TopicPartition> partitions, OffsetSpec spec) throws Exception {
        if (partitions.isEmpty()) {
            return Map.of();
        }
        ListOffsetsResult result = admin.listOffsets(partitions.stream().collect(Collectors.toMap(Function.identity(), p -> spec)));
        return result.all().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));
    }

    private Map<String, String> retention(Set<String> topics) throws Exception {
        List<ConfigResource> resources = topics.stream().map(t -> new ConfigResource(ConfigResource.Type.TOPIC, t)).toList();
        Map<String, String> retention = new HashMap<>();
        admin.describeConfigs(resources).all().get().forEach((resource, config) -> {
            var entry = config.get(TopicConfig.RETENTION_MS_CONFIG);
            if (entry != null) {
                retention.put(resource.name(), entry.value());
            }
        });
        return retention;
    }

    static void requireTopic(String topic) {
        if (topic == null || !TOPIC.matcher(topic).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a topic name");
        }
    }

    /** "0:120,1:45" -> {0=120, 1=45}. */
    static Map<Integer, Long> parseCursor(String cursor) {
        Map<Integer, Long> position = new HashMap<>();
        if (cursor == null || cursor.isBlank()) {
            return position;
        }
        try {
            for (String part : cursor.split(",")) {
                String[] pair = part.split(":");
                position.put(Integer.parseInt(pair[0].trim()), Long.parseLong(pair[1].trim()));
            }
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cursor must look like 0:120,1:45");
        }
        return position;
    }

    /** UTF-8 text when the bytes are valid UTF-8, else base64 (binary = true). */
    static Decoded decode(byte[] bytes, int maxChars) {
        if (bytes == null) {
            return new Decoded(null, false, false);
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return text.length() > maxChars
                    ? new Decoded(text.substring(0, maxChars), false, true)
                    : new Decoded(text, false, false);
        } catch (CharacterCodingException e) {
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return base64.length() > maxChars
                    ? new Decoded(base64.substring(0, maxChars), true, true)
                    : new Decoded(base64, true, false);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private <T> T call(KafkaCall<T> call) {
        try {
            return call.run();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kafka is unavailable right now", e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kafka is unavailable right now", e);
        }
    }

    @FunctionalInterface
    private interface KafkaCall<T> {
        T run() throws Exception;
    }

    record Decoded(String text, boolean binary, boolean truncated) {
    }

    /**
     * @param retentionMs how long Kafka keeps messages ("-1" = forever), null if unknown
     * @param readers     consumer groups with committed offsets on it
     */
    public record TopicView(String name, int partitions, long messages, boolean deadLetter, boolean internal,
                            String retentionMs, List<String> readers) {
    }

    /** @param binary value is base64 (not UTF-8); @param envelope the event's fields when it is a SmartConnect event */
    public record Message(int partition, long offset, Instant timestamp, String key, List<HeaderView> headers,
                          String value, boolean binary, boolean truncated, int size, Envelope envelope) {
    }

    public record HeaderView(String key, String value, boolean binary) {
    }

    public record Envelope(String id, String type, String version, String source, String occurredAt, String correlationId) {
    }

    /** @param next cursor for the following page (further back for newest, forward for oldest), null at the end */
    public record MessagePage(String topic, String order, List<Message> messages, String next) {
    }

    public record GroupTopic(String topic, long lag, int partitions) {
    }

    public record GroupView(String groupId, String state, int members, long lag, List<GroupTopic> topics) {
    }
}
