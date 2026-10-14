package com.smartconnect.apigateway.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One request's path through the platform, from the logs: every line carrying its correlation
 * id ({@code smartconnect-logs-*}, field {@code correlation_id} - lifted out of the log format by
 * Filebeat), in time order, across the gateway, service-to-service calls and Kafka consumers.
 */
@Component
public class RequestTraces {

    /** Enough for any single request; a runaway loop doesn't flood the backoffice. */
    static final int MAX_LINES = 300;
    private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    /** The fleet log format's prefix (see infra/consul/config/application.yml), shown as fields instead. */
    private static final Pattern LOG_PREFIX = Pattern.compile("^\\S+ \\S+ \\[[^\\]]*\\] \\w+\\s+\\S+ \\[[^\\]]*\\] - ");
    private static final String CONTAINER_PREFIX = "smartconnect-";

    private final WebClient elasticsearch;
    private final String index;
    private final ObjectMapper json = new ObjectMapper();

    public RequestTraces(WebClient.Builder builder,
                         @Value("${smartconnect.platform.metrics.elasticsearch-url:${management.elastic.metrics.export.host:http://elasticsearch:9200}}") String url,
                         @Value("${smartconnect.platform.logs.index:smartconnect-logs-*}") String index) {
        this.elasticsearch = builder.baseUrl(url).build();
        this.index = index;
    }

    public Mono<Trace> find(String correlationId) {
        if (!CORRELATION_ID.matcher(correlationId).matches()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a correlation id"));
        }
        return elasticsearch.post()
                .uri("/{index}/_search", index)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query(correlationId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(5))
                .map(response -> parse(response, correlationId))
                .onErrorMap(e -> !(e instanceof ResponseStatusException),
                        e -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Logs are unavailable right now", e));
    }

    /** Built as a tree, not a string: the id can't change the query's shape. */
    String query(String correlationId) {
        ObjectNode query = json.createObjectNode();
        query.put("size", MAX_LINES + 1);
        query.put("track_total_hits", false);
        query.putArray("sort").addObject().put("@timestamp", "asc");
        query.putArray("_source").add("@timestamp").add("container.name").add("log.level").add("log.logger")
                .add("trace.id").add("message");
        ObjectNode bool = query.putObject("query").putObject("bool");
        bool.putArray("filter")
                .add(json.createObjectNode().set("term", json.createObjectNode().put("correlation_id", correlationId)))
                .add(json.createObjectNode().set("range", json.createObjectNode().set("@timestamp",
                        json.createObjectNode().put("gte", "now-7d"))));
        return query.toString();
    }

    static Trace parse(JsonNode response, String correlationId) {
        List<Line> lines = new ArrayList<>();
        Set<String> services = new LinkedHashSet<>();
        Set<String> traceIds = new LinkedHashSet<>();
        JsonNode hits = response.path("hits").path("hits");
        for (int i = 0; i < Math.min(hits.size(), MAX_LINES); i++) {
            JsonNode source = hits.get(i).path("_source");
            String service = service(source.path("container").path("name").asText(""));
            String traceId = textOrNull(source.path("trace").path("id"));
            services.add(service);
            if (traceId != null) {
                traceIds.add(traceId);
            }
            lines.add(new Line(Instant.parse(source.path("@timestamp").asText()), service,
                    textOrNull(source.path("log").path("level")), textOrNull(source.path("log").path("logger")),
                    LOG_PREFIX.matcher(source.path("message").asText("")).replaceFirst(""), traceId));
        }
        Long durationMs = lines.size() < 2 ? null
                : Duration.between(lines.get(0).t(), lines.get(lines.size() - 1).t()).toMillis();
        return new Trace(correlationId, List.copyOf(services), List.copyOf(traceIds), durationMs,
                hits.size() > MAX_LINES, lines);
    }

    /** smartconnect-user-service -> user-service */
    private static String service(String container) {
        return container.startsWith(CONTAINER_PREFIX) ? container.substring(CONTAINER_PREFIX.length()) : container;
    }

    private static String textOrNull(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    /**
     * @param services  in the order the request reached them
     * @param traceIds  the distributed traces it belongs to (Jaeger)
     * @param truncated more than {@value #MAX_LINES} lines matched; the first ones are shown
     */
    public record Trace(String correlationId, List<String> services, List<String> traceIds, Long durationMs,
                        boolean truncated, List<Line> lines) {
    }

    /** One log line; {@code message} without the log format's prefix, which the other fields carry. */
    public record Line(Instant t, String service, String level, String logger, String message, String traceId) {
    }
}
