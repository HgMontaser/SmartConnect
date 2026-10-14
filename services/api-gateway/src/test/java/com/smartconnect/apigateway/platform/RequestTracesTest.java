package com.smartconnect.apigateway.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTracesTest {

    private final ObjectMapper json = new ObjectMapper();
    private final RequestTraces traces = new RequestTraces(WebClient.builder(), "http://localhost:1", "logs-*");

    @Test
    void queryFiltersOnTheCorrelationIdInTimeOrder() throws Exception {
        JsonNode query = json.readTree(traces.query("abc-123"));

        assertThat(query.at("/query/bool/filter/0/term/correlation_id").asText()).isEqualTo("abc-123");
        assertThat(query.at("/sort/0/@timestamp").asText()).isEqualTo("asc");
        assertThat(query.path("size").asInt()).isEqualTo(RequestTraces.MAX_LINES + 1);
    }

    @Test
    void refusesSomethingThatIsNotACorrelationId() {
        StepVerifier.create(traces.find("x\" } }, { \"match_all"))
                .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400"))
                .verify();
    }

    @Test
    void parsesLinesServicesInOrderOfArrivalAndTheDuration() throws Exception {
        JsonNode response = json.readTree("""
                { "hits": { "hits": [
                  { "_source": { "@timestamp": "2026-10-14T19:00:00.000Z", "container": { "name": "smartconnect-api-gateway" },
                    "log": { "level": "INFO", "logger": "c.s.a.l.CorrelationLoggingGlobalFilter" }, "trace": { "id": "t1" },
                    "message": "2026-10-14 19:00:00,000 [boundedElastic-1] INFO  c.s.a.l.CorrelationLoggingGlobalFilter [trace=t1 span=s1 correlationId=abc] - POST /api/users" } },
                  { "_source": { "@timestamp": "2026-10-14T19:00:00.120Z", "container": { "name": "smartconnect-user-service" },
                    "log": { "level": "INFO", "logger": "c.s.u.UserService" }, "trace": { "id": "t1" },
                    "message": "2026-10-14 19:00:00,120 [http-nio-8081-exec-1] INFO  c.s.u.UserService [trace=t1 span=s2 correlationId=abc] - created user x" } },
                  { "_source": { "@timestamp": "2026-10-14T19:00:00.180Z", "container": { "name": "smartconnect-api-gateway" },
                    "log": { "level": "INFO" }, "message": "plain line" } } ] } }
                """);

        RequestTraces.Trace trace = RequestTraces.parse(response, "abc");

        assertThat(trace.services()).containsExactly("api-gateway", "user-service");
        assertThat(trace.traceIds()).containsExactly("t1");
        assertThat(trace.durationMs()).isEqualTo(180);
        assertThat(trace.truncated()).isFalse();
        assertThat(trace.lines().get(1)).isEqualTo(new RequestTraces.Line(Instant.parse("2026-10-14T19:00:00.120Z"),
                "user-service", "INFO", "c.s.u.UserService", "created user x", "t1"));
        assertThat(trace.lines().get(2).message()).isEqualTo("plain line");
        assertThat(trace.lines().get(2).logger()).isNull();
    }

    @Test
    void capsTheLinesAndSaysSo() {
        ObjectNode response = json.createObjectNode();
        ArrayNode hits = response.putObject("hits").putArray("hits");
        for (int i = 0; i <= RequestTraces.MAX_LINES; i++) {
            ObjectNode source = hits.addObject().putObject("_source");
            source.put("@timestamp", Instant.parse("2026-10-14T19:00:00Z").plusMillis(i).toString());
            source.putObject("container").put("name", "smartconnect-user-service");
            source.put("message", "line " + i);
        }

        RequestTraces.Trace trace = RequestTraces.parse(response, "abc");

        assertThat(trace.lines()).hasSize(RequestTraces.MAX_LINES);
        assertThat(trace.truncated()).isTrue();
    }

    @Test
    void noLinesIsAnEmptyTraceNotAnError() throws Exception {
        RequestTraces.Trace trace = RequestTraces.parse(json.readTree("{ \"hits\": { \"hits\": [] } }"), "abc");

        assertThat(trace.lines()).isEmpty();
        assertThat(trace.durationMs()).isNull();
    }
}
