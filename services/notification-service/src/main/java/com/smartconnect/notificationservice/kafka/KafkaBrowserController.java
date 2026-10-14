package com.smartconnect.notificationservice.kafka;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Kafka browsing for the backoffice Events page: topics, their messages, consumer groups. */
@RestController
@RequestMapping("/api/events")
public class KafkaBrowserController {

    private final KafkaBrowser kafka;

    public KafkaBrowserController(KafkaBrowser kafka) {
        this.kafka = kafka;
    }

    @GetMapping("/topics")
    @PreAuthorize("hasAuthority('events:read')")
    public List<KafkaBrowser.TopicView> topics() {
        return kafka.topics();
    }

    /**
     * @param order  newest (default; {@code cursor} goes further back) or oldest ({@code cursor} goes forward)
     * @param cursor a previous page's {@code next}
     */
    @GetMapping("/topics/{topic}/messages")
    @PreAuthorize("hasAuthority('events:read')")
    public KafkaBrowser.MessagePage messages(@PathVariable String topic,
                                             @RequestParam(required = false) Integer partition,
                                             @RequestParam(defaultValue = "newest") String order,
                                             @RequestParam(defaultValue = "50") int limit,
                                             @RequestParam(required = false) String cursor) {
        return kafka.messages(topic, partition, order, limit, cursor);
    }

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('events:read')")
    public List<KafkaBrowser.GroupView> groups() {
        return kafka.groups();
    }
}
