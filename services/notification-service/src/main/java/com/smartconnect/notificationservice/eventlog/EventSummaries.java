package com.smartconnect.notificationservice.eventlog;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One event in words, for the backoffice's live activity: who did it (when the event says) and
 * what happened, as a verb phrase - "admin.seed" + "created user alice". The backoffice joins a
 * request's events (its domain event and its audit record) into one line by correlation id, so
 * each kind only says what it knows best: user events the user, audit records the person.
 * Unknown event types still read sensibly from their name.
 */
public final class EventSummaries {

    private static final Map<String, String> VERBS = Map.of(
            "POST", "created", "PUT", "updated", "PATCH", "updated", "DELETE", "deleted");
    private static final List<String> ACTOR_FIELDS = List.of("actor", "author", "createdBy", "username", "user");

    private EventSummaries() {
    }

    /** @param actor who did it, null if the event doesn't say */
    public record Summary(String actor, String text) {
    }

    public static Summary describe(String topic, String type, String source, JsonNode payload, boolean deadLetter, String error) {
        if (deadLetter) {
            String what = type != null ? type : "a record";
            return new Summary(null, "couldn't process " + what + (source != null ? " from " + source : "")
                    + (error != null ? " - " + error : ""));
        }
        if (type == null) {
            if (payload != null && payload.has("permissions")) {
                int count = payload.path("permissions").size();
                return new Summary(text(payload, "service"),
                        "published its permission catalog (" + count + " permission" + (count == 1 ? "" : "s") + ")");
            }
            return new Summary(null, "wrote a record to " + topic);
        }
        if (type.startsWith("user.") && payload != null) {
            return new Summary(null, userVerb(type) + " user " + text(payload, "username") + roles(type, payload));
        }
        if ("audit.performed".equals(type) && payload != null) {
            return audit(payload);
        }
        return new Summary(payload == null ? null : actor(payload), humanize(type));
    }

    private static String userVerb(String type) {
        return switch (type) {
            case "user.created" -> "created";
            case "user.deleted" -> "deleted";
            case "user.updated" -> "updated";
            default -> type.substring("user.".length()).replace('-', ' ');
        };
    }

    private static String roles(String type, JsonNode payload) {
        JsonNode roles = payload.path("realmRoles");
        if (!"user.created".equals(type) || !roles.isArray() || roles.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        roles.forEach(r -> names.add(r.asText()));
        return " with role" + (names.size() == 1 ? " " : "s ") + String.join(", ", names);
    }

    /** "DELETE /api/users/{id}" by admin.seed -> admin.seed / "deleted a user". */
    private static Summary audit(JsonNode payload) {
        String actor = text(payload, "actor");
        String method = text(payload, "method");
        String action = text(payload, "action");
        String path = action != null && action.contains(" ") ? action.substring(action.indexOf(' ') + 1) : action;
        String phrase = phrase(method, path, payload.path("targets"));
        int status = payload.path("status").asInt(0);
        if (status >= 400) {
            String reason = status == 401 || status == 403 ? "denied" : "failed";
            return new Summary(actor, "tried to " + toInfinitive(phrase) + " - " + reason + " (" + status + ")");
        }
        return new Summary(actor, phrase);
    }

    /**
     * The resource is the last literal path segment. When it follows a {variable} it is an action
     * on that resource ("POST /api/users/{id}/reset-password" -> "ran reset password on a user").
     */
    static String phrase(String method, String path, JsonNode targets) {
        if (path == null) {
            return "called an API";
        }
        String[] segments = path.replaceFirst("^/api/", "").split("/");
        int last = segments.length - 1;
        while (last >= 0 && isVariable(segments[last])) {
            last--;
        }
        if (last < 0) {
            return "called " + path;
        }
        String verb = VERBS.getOrDefault(method, method == null ? "called" : method.toLowerCase());
        boolean actionOnResource = last > 0 && isVariable(segments[last - 1]);
        if (actionOnResource) {
            String resource = singular(segments[last - 2 >= 0 ? last - 2 : 0]);
            return "ran " + segments[last].replace('-', ' ') + " on " + named(resource, segments[last - 1], targets);
        }
        String resource = singular(segments[last]);
        boolean onOne = last < segments.length - 1;
        return verb + " " + (onOne ? named(resource, segments[last + 1], targets) : article(resource) + " " + resource);
    }

    /** "role" + "{name}" + {name: admin} -> "role admin"; ids aren't worth showing -> "a user". */
    private static String named(String resource, String variable, JsonNode targets) {
        String key = variable.substring(1, variable.length() - 1);
        String value = targets == null ? null : text(targets, key);
        if (value != null && !"id".equals(key) && !key.endsWith("Id")) {
            return resource + " " + value;
        }
        return article(resource) + " " + resource;
    }

    private static String toInfinitive(String phrase) {
        for (Map.Entry<String, String> verb : Map.of("created", "create", "updated", "update", "deleted", "delete", "ran", "run").entrySet()) {
            if (phrase.startsWith(verb.getKey() + " ")) {
                return verb.getValue() + phrase.substring(verb.getKey().length());
            }
        }
        return phrase;
    }

    /** "demo.note-created" -> "note created"; the domain prefix is the source's own name. */
    static String humanize(String type) {
        String name = type.contains(".") ? type.substring(type.indexOf('.') + 1) : type;
        return name.replace('-', ' ').replace('.', ' ').replace('_', ' ');
    }

    private static String actor(JsonNode payload) {
        for (String field : ACTOR_FIELDS) {
            String value = text(payload, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean isVariable(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    private static String singular(String word) {
        if (word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.endsWith("sses") || word.endsWith("ches")) {
            return word.substring(0, word.length() - 2);
        }
        return word.endsWith("s") && !word.endsWith("ss") ? word.substring(0, word.length() - 1) : word;
    }

    /** By sound, roughly: resource names starting with "u" say "you" (a user, a unit). */
    private static String article(String word) {
        return !word.isEmpty() && "aeio".indexOf(Character.toLowerCase(word.charAt(0))) >= 0 ? "an" : "a";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isValueNode() && !value.asText().isBlank() ? value.asText() : null;
    }
}
