package com.smartconnect.commons.messaging;

import java.util.regex.Pattern;

/**
 * Naming rules. An event type is {@code <domain>.<event>}; all events of a domain share the
 * topic {@code events.<domain>} (keyed by entity id, so one entity's events stay in order),
 * and each consuming service has its own dead-letter topic {@code events.<domain>.<service>.dlt}.
 */
public final class EventTopics {

    public static final Pattern TYPE = Pattern.compile("^[a-z][a-z0-9-]{0,29}\\.[a-z][a-z0-9-]{0,59}$");
    private static final String PREFIX = "events.";
    private static final String DLT_SUFFIX = ".dlt";

    private EventTopics() {
    }

    public static String forType(String type) {
        if (type == null || !TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("Event type must look like <domain>.<event> (lowercase, '-'): " + type);
        }
        return PREFIX + type.substring(0, type.indexOf('.'));
    }

    public static String forDomain(String domain) {
        return PREFIX + domain;
    }

    public static String deadLetter(String topic, String consumer) {
        return topic + "." + consumer + DLT_SUFFIX;
    }

    public static boolean isDeadLetter(String topic) {
        return topic.endsWith(DLT_SUFFIX);
    }
}
