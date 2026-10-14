package com.smartconnect.notificationservice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LiveEventBroadcasterTest {

    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    private final LiveEventBroadcaster broadcaster = new LiveEventBroadcaster(json);

    private FakeSession session(String... authorities) {
        FakeSession session = new FakeSession(new TestingAuthenticationToken("someone", null, authorities));
        broadcaster.register(session);
        return session;
    }

    private static LiveEventBroadcaster.LiveEvent event(String type, boolean withUser) throws Exception {
        return new LiveEventBroadcaster.LiveEvent("events.x", 0, 1, "k", "e1", type, "svc", Instant.EPOCH, false, null, "c1", null, "did something",
                withUser ? new ObjectMapper().readTree("{\"userId\":\"u1\",\"eventType\":\"USER_CREATED\"}") : null);
    }

    @Test
    void userEventsReachEverySignedInSession() throws Exception {
        FakeSession plain = session("users:read");
        FakeSession admin = session("events:read");

        broadcaster.broadcast(event("user.created", true));

        assertThat(plain.sent).hasSize(1);
        assertThat(admin.sent).hasSize(1);
        assertThat(plain.sent.get(0)).contains("\"userId\":\"u1\"");
    }

    @Test
    void otherEventsOnlyReachSessionsThatMayReadEvents() throws Exception {
        FakeSession plain = session("users:read");
        FakeSession admin = session("events:read");

        broadcaster.broadcast(event("audit.performed", false));

        assertThat(plain.sent).isEmpty();
        assertThat(admin.sent).singleElement().asString().contains("\"type\":\"audit.performed\"");
    }

    /** Just enough of a session to record what was sent to it. */
    private static final class FakeSession implements WebSocketSession {
        final List<String> sent = new ArrayList<>();
        private final Principal principal;

        FakeSession(Principal principal) {
            this.principal = principal;
        }

        @Override public void sendMessage(WebSocketMessage<?> message) { sent.add(message.getPayload().toString()); }
        @Override public Principal getPrincipal() { return principal; }
        @Override public boolean isOpen() { return true; }
        @Override public String getId() { return Integer.toHexString(System.identityHashCode(this)); }
        @Override public URI getUri() { return null; }
        @Override public HttpHeaders getHandshakeHeaders() { return new HttpHeaders(); }
        @Override public Map<String, Object> getAttributes() { return Map.of(); }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol() { return null; }
        @Override public void setTextMessageSizeLimit(int messageSizeLimit) { }
        @Override public int getTextMessageSizeLimit() { return 0; }
        @Override public void setBinaryMessageSizeLimit(int messageSizeLimit) { }
        @Override public int getBinaryMessageSizeLimit() { return 0; }
        @Override public List<WebSocketExtension> getExtensions() { return List.of(); }
        @Override public void close() { }
        @Override public void close(CloseStatus status) { }
    }
}
