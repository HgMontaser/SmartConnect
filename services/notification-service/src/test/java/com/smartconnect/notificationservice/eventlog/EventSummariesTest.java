package com.smartconnect.notificationservice.eventlog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventSummariesTest {

    private final ObjectMapper json = new ObjectMapper();

    private EventSummaries.Summary describe(String topic, String type, String payload) throws Exception {
        JsonNode node = payload == null ? null : json.readTree(payload);
        return EventSummaries.describe(topic, type, "svc", node, false, null);
    }

    private EventSummaries.Summary audit(String method, String action, String targets, int status) throws Exception {
        return describe("events.audit", "audit.performed", """
                {"actor":"admin.seed","method":"%s","action":"%s","targets":%s,"status":%d}
                """.formatted(method, action, targets, status));
    }

    @Test
    void userEventsSayWhatHappenedToWhom() throws Exception {
        assertThat(describe("events.user", "user.created", "{\"username\":\"alice\",\"realmRoles\":[\"admin\",\"user\"]}"))
                .isEqualTo(new EventSummaries.Summary(null, "created user alice with roles admin, user"));
        assertThat(describe("events.user", "user.deleted", "{\"username\":\"alice\",\"realmRoles\":[]}").text())
                .isEqualTo("deleted user alice");
    }

    @Test
    void auditRecordsSayWhoDidWhat() throws Exception {
        assertThat(audit("POST", "POST /api/users", "{}", 201))
                .isEqualTo(new EventSummaries.Summary("admin.seed", "created a user"));
        assertThat(audit("DELETE", "DELETE /api/users/{id}", "{\"id\":\"u-1\"}", 204).text()).isEqualTo("deleted a user");
        assertThat(audit("PUT", "PUT /api/roles/{name}", "{\"name\":\"auditor\"}", 200).text()).isEqualTo("updated role auditor");
        assertThat(audit("POST", "POST /api/demo/notes", "{}", 201).text()).isEqualTo("created a note");
        assertThat(audit("POST", "POST /api/users/{id}/reset-password", "{\"id\":\"u-1\"}", 204).text())
                .isEqualTo("ran reset password on a user");
        assertThat(audit("POST", "POST /api/permissions", "{}", 201).text()).isEqualTo("created a permission");
    }

    @Test
    void refusedAttemptsSaySo() throws Exception {
        assertThat(audit("DELETE", "DELETE /api/users/{id}", "{\"id\":\"u-1\"}", 403).text())
                .isEqualTo("tried to delete a user - denied (403)");
        assertThat(audit("POST", "POST /api/users", "{}", 500).text()).isEqualTo("tried to create a user - failed (500)");
    }

    @Test
    void otherServicesEventsReadFromTheirNameAndPayload() throws Exception {
        assertThat(describe("events.demo", "demo.note-created", "{\"text\":\"hi\",\"author\":\"admin.seed\"}"))
                .isEqualTo(new EventSummaries.Summary("admin.seed", "note created"));
        assertThat(describe("permission-catalog", null, "{\"service\":\"demo-service\",\"permissions\":{\"demo:read\":\"x\",\"demo:write\":\"y\"}}"))
                .isEqualTo(new EventSummaries.Summary("demo-service", "published its permission catalog (2 permissions)"));
        assertThat(describe("misc", null, null).text()).isEqualTo("wrote a record to misc");
    }

    @Test
    void deadLettersSayWhatFailed() {
        assertThat(EventSummaries.describe("events.demo.demo-service.dlt", "demo.note-created", "demo-service", null, true, "boom").text())
                .isEqualTo("couldn't process demo.note-created from demo-service - boom");
    }
}
