package com.smartconnect.demoservice;

import com.smartconnect.commons.messaging.EventEnvelope;
import com.smartconnect.commons.messaging.EventListeners;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consumes demo events through the standard factory and keeps an activity feed. A note whose
 * text contains "fail" makes the handler throw, to show the platform's failure handling: three
 * retries with backoff, then the record lands on events.demo.demo-service.dlt - which this class
 * also reads, to show what was parked and why.
 */
@RestController
@RequestMapping("/api/demo")
public class ActivityListener {

    private static final int MAX_ITEMS = 20;

    private final List<Activity> processed = new CopyOnWriteArrayList<>();
    private final List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();
    private final AtomicInteger attempts = new AtomicInteger();

    @KafkaListener(topics = "events.demo", containerFactory = EventListeners.FACTORY)
    public void on(EventEnvelope event) {
        if (!event.type().equals("demo.note-created")) {
            return;
        }
        NotesController.Note note = event.payloadAs(NotesController.Note.class);
        attempts.incrementAndGet();
        if (note.text().toLowerCase().contains("fail")) {
            throw new IllegalStateException("Simulated consumer bug for note \"" + note.text() + "\"");
        }
        add(processed, new Activity(event.id().toString(), event.type(), note.author() + " wrote \"" + note.text() + "\"", event.occurredAt()));
    }

    /** Raw view of the dead-letter topic: the original record plus the error headers Spring Kafka adds. */
    @KafkaListener(topics = "events.demo.demo-service.dlt", groupId = "demo-service-dlt-viewer")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        add(deadLetters, new DeadLetter(record.key(), header(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
                header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC), Instant.ofEpochMilli(record.timestamp())));
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAuthority('demo:read')")
    public Map<String, Object> activity() {
        return Map.of("processed", processed, "deadLetters", deadLetters, "handlerAttempts", attempts.get());
    }

    private static <T> void add(List<T> list, T item) {
        list.add(0, item);
        while (list.size() > MAX_ITEMS) {
            list.remove(list.size() - 1);
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    public record Activity(String eventId, String type, String summary, Instant occurredAt) {
    }

    public record DeadLetter(String key, String error, String originalTopic, Instant parkedAt) {
    }
}
