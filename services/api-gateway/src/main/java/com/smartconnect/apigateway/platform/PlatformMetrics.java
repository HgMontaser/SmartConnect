package com.smartconnect.apigateway.platform;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Request metrics per service, read from the Micrometer documents every service pushes to
 * Elasticsearch ({@code smartconnect-metrics-*}, {@code http.server.requests}). One query per
 * call returns, per service, a time series over the chosen range and a "now" summary over the
 * last 5 complete minutes - ready to draw, so the backoffice never talks to Elasticsearch.
 *
 * <p>Elasticsearch being down fails only these calls (503); routing doesn't depend on it.
 */
@Component
public class PlatformMetrics {

    /** The summary window: the last 5 minutes, minus the current one, which is still filling. */
    private static final String RECENT_FROM = "now-6m";
    private static final String RECENT_TO = "now-1m";

    private final WebClient elasticsearch;
    private final String index;

    public PlatformMetrics(WebClient.Builder builder,
                           @Value("${smartconnect.platform.metrics.elasticsearch-url:${management.elastic.metrics.export.host:http://elasticsearch:9200}}") String url,
                           @Value("${smartconnect.platform.metrics.index:smartconnect-metrics-*}") String index) {
        this.elasticsearch = builder.baseUrl(url).build();
        this.index = index;
    }

    /** The ranges the backoffice offers, each with a bucket size that keeps ~30-100 points. */
    public enum Range {
        M15("15m", Duration.ofMinutes(15), Duration.ofSeconds(30)),
        H1("1h", Duration.ofHours(1), Duration.ofMinutes(1)),
        H6("6h", Duration.ofHours(6), Duration.ofMinutes(5)),
        H24("24h", Duration.ofHours(24), Duration.ofMinutes(15));

        final String key;
        final Duration span;
        final Duration bucket;

        Range(String key, Duration span, Duration bucket) {
            this.key = key;
            this.span = span;
            this.bucket = bucket;
        }

        static Range of(String key) {
            return Arrays.stream(values()).filter(r -> r.key.equals(key)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "range must be one of 15m, 1h, 6h, 24h"));
        }
    }

    public Mono<MetricsView> fetch(Range range) {
        return elasticsearch.post()
                .uri("/{index}/_search", index)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query(range))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(5))
                .map(response -> parse(response, range))
                .onErrorMap(e -> !(e instanceof ResponseStatusException),
                        e -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Metrics are unavailable right now", e));
    }

    static String query(Range range) {
        // Per bucket: requests (sum of counts), 5xx, and the p95 of the slowest endpoint (max over
        // the per-endpoint percentile gauges). Actuator calls (health probes) are left out.
        String measures = """
                "requests": { "filter": { "term": { "name": "http_server_requests" } },
                              "aggs": { "n": { "sum": { "field": "count" } } } },
                "errors":   { "filter": { "bool": { "filter": [
                                { "term": { "name": "http_server_requests" } },
                                { "term": { "outcome.keyword": "SERVER_ERROR" } } ] } },
                              "aggs": { "n": { "sum": { "field": "count" } } } },
                "p95":      { "filter": { "bool": { "filter": [
                                { "term": { "name": "http_server_requests_percentile" } },
                                { "term": { "phi.keyword": "0.95" } } ] } },
                              "aggs": { "ms": { "max": { "field": "value" } } } }
                """;
        return """
                {
                  "size": 0,
                  "query": { "bool": {
                    "filter": [
                      { "terms": { "name": ["http_server_requests", "http_server_requests_percentile"] } },
                      { "range": { "@timestamp": { "gte": "now-%1$s" } } } ],
                    "must_not": [ { "prefix": { "uri.keyword": "/actuator" } } ] } },
                  "aggs": { "services": {
                    "terms": { "field": "service.keyword", "size": 100 },
                    "aggs": {
                      "series": {
                        "date_histogram": { "field": "@timestamp", "fixed_interval": "%2$ds",
                                            "min_doc_count": 0,
                                            "extended_bounds": { "min": "now-%1$s", "max": "now" } },
                        "aggs": { %3$s } },
                      "recent": {
                        "filter": { "range": { "@timestamp": { "gte": "%4$s", "lt": "%5$s" } } },
                        "aggs": { %3$s } } } } }
                }
                """.formatted(range.key, range.bucket.toSeconds(), measures, RECENT_FROM, RECENT_TO);
    }

    static MetricsView parse(JsonNode response, Range range) {
        List<ServiceMetrics> services = new ArrayList<>();
        for (JsonNode service : response.path("aggregations").path("services").path("buckets")) {
            List<Point> series = new ArrayList<>();
            for (JsonNode bucket : service.path("series").path("buckets")) {
                series.add(new Point(bucket.path("key").asLong(), requests(bucket), errors(bucket), p95(bucket)));
            }
            JsonNode recent = service.path("recent");
            double requests = requests(recent);
            services.add(new ServiceMetrics(
                    service.path("key").asText(),
                    requests / 5.0,
                    requests > 0 ? errors(recent) / requests : null,
                    p95(recent),
                    series));
        }
        return new MetricsView(range.key, range.bucket.toSeconds(), services);
    }

    private static double requests(JsonNode bucket) {
        return bucket.path("requests").path("n").path("value").asDouble(0);
    }

    private static double errors(JsonNode bucket) {
        return bucket.path("errors").path("n").path("value").asDouble(0);
    }

    /**
     * Null without traffic: no percentile reported, or 0 - Micrometer's percentile gauges read 0
     * once their window has no requests, which would draw as a misleading 0 ms.
     */
    private static Double p95(JsonNode bucket) {
        JsonNode ms = bucket.path("p95").path("ms").path("value");
        return ms.isNumber() && ms.asDouble() > 0 ? ms.asDouble() : null;
    }

    /**
     * @param requestsPerMinute last 5 complete minutes
     * @param errorRate         5xx share of those requests, null without traffic
     * @param p95Ms             p95 latency of the slowest endpoint in that window, null without traffic
     */
    public record ServiceMetrics(String service, double requestsPerMinute, Double errorRate, Double p95Ms, List<Point> series) {
    }

    /** One bucket: its start (epoch ms), request and 5xx counts, slowest endpoint's p95. */
    public record Point(long t, double requests, double errors, Double p95Ms) {
    }

    public record MetricsView(String range, long bucketSeconds, List<ServiceMetrics> services) {
    }
}
