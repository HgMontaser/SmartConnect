package com.smartconnect.notificationservice.eventlog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartconnect.commons.messaging.EventTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Taps every topic (events.*, dead letters, and the services' other topics) for the backoffice
 * Events page's live view. Reads raw strings rather than envelopes so it can show anything - including
 * records that failed to decode. Keeps the last {@value #CAPACITY} in memory: on startup it
 * reads each topic from the beginning (a fresh consumer group per instance), then follows live.
 * That's fine for local topics; with large production topics it should start from recent
 * offsets instead.
 */
@Component
public class EventLog {

    static final int CAPACITY = 200;
    static final int AUDIT_CAPACITY = 500;

    private final Deque<EventLogEntry> entries = new ArrayDeque<>();
    /** audit.performed events, kept apart so routine traffic never pushes them out. */
    private final Deque<AuditEntry> audit = new ArrayDeque<>();
    private final ObjectMapper objectMapper;

    public EventLog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Every topic but Kafka's internal ones (__consumer_offsets, ...): events.*, dead letters, and
    // the services' other topics such as permission-catalog.
    @KafkaListener(topicPattern = "[^_].*", containerFactory = EventLogConsumerConfig.FACTORY)
    public void on(ConsumerRecord<String, String> record) {
        boolean deadLetter = EventTopics.isDeadLetter(record.topic());
        JsonNode event = parse(record.value());
        EventLogEntry entry = new EventLogEntry(
                record.topic(), record.partition(), record.offset(), record.key(),
                text(event, "id"), text(event, "type"), text(event, "source"),
                Instant.ofEpochMilli(record.timestamp()),
                deadLetter,
                deadLetter ? header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE) : null,
                deadLetter ? header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC) : null,
                event == null ? truncate(record.value()) : null,
                text(event, "correlationId"));
        synchronized (entries) {
            entries.addFirst(entry);
            while (entries.size() > CAPACITY) {
                entries.removeLast();
            }
        }
        if (!deadLetter && event != null && "audit.performed".equals(entry.type()) && event.hasNonNull("payload")) {
            synchronized (audit) {
                audit.addFirst(new AuditEntry(entry.eventId(), entry.source(), entry.timestamp(), entry.correlationId(), event.get("payload")));
                while (audit.size() > AUDIT_CAPACITY) {
                    audit.removeLast();
                }
            }
        }
    }

    public List<AuditEntry> audit(int limit) {
        synchronized (audit) {
            return audit.stream()
                    .sorted(java.util.Comparator.comparing(AuditEntry::timestamp).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    /** Newest first by record time - topics are replayed in no particular order on startup. */
    public List<EventLogEntry> recent(int limit) {
        synchronized (entries) {
            return entries.stream()
                    .sorted(java.util.Comparator.comparing(EventLogEntry::timestamp).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    private JsonNode parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        return node == null || !node.hasNonNull(field) ? null : node.get(field).asText();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String truncate(String value) {
        return value == null ? null : value.length() > 200 ? value.substring(0, 200) + "…" : value;
    }

    /** @param correlationId the request that caused it - the backoffice traces it through the logs */
    public record AuditEntry(String eventId, String source, Instant timestamp, String correlationId, JsonNode record) {
    }

    public record EventLogEntry(String topic, int partition, long offset, String key, String eventId, String type,
                                String source, Instant timestamp, boolean deadLetter, String error,
                                String originalTopic, String raw, String correlationId) {
    }
}
