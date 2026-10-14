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
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

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

    private static final String SERVER_TIMER = "http_server_requests";
    private static final String GATEWAY_TIMER = "spring_cloud_gateway_requests";
    /** Service names go into the query, so only what Consul service names look like. */
    private static final Pattern SERVICE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,62}");

    private final WebClient elasticsearch;
    private final String index;
    private final String gatewayName;

    public PlatformMetrics(WebClient.Builder builder,
                           @Value("${smartconnect.platform.metrics.elasticsearch-url:${management.elastic.metrics.export.host:http://elasticsearch:9200}}") String url,
                           @Value("${smartconnect.platform.metrics.index:smartconnect-metrics-*}") String index,
                           @Value("${spring.application.name}") String gatewayName) {
        this.elasticsearch = builder.baseUrl(url).build();
        this.index = index;
        this.gatewayName = gatewayName;
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

    /**
     * Requests (sum of counts), 5xx, optionally 429s, and p95 (max over the percentile gauges, so
     * the slowest of whatever the bucket groups) of one Micrometer timer.
     */
    static String measures(String timer, boolean withRateLimited) {
        String rateLimited = !withRateLimited ? "" : """
                ,
                "rateLimited": { "filter": { "bool": { "filter": [
                                { "term": { "name": "%1$s" } },
                                { "term": { "httpStatusCode.keyword": "429" } } ] } },
                              "aggs": { "n": { "sum": { "field": "count" } } } }
                """.formatted(timer);
        return """
                "requests": { "filter": { "term": { "name": "%1$s" } },
                              "aggs": { "n": { "sum": { "field": "count" } } } },
                "errors":   { "filter": { "bool": { "filter": [
                                { "term": { "name": "%1$s" } },
                                { "term": { "outcome.keyword": "SERVER_ERROR" } } ] } },
                              "aggs": { "n": { "sum": { "field": "count" } } } },
                "p95":      { "filter": { "bool": { "filter": [
                                { "term": { "name": "%1$s_percentile" } },
                                { "term": { "phi.keyword": "0.95" } } ] } },
                              "aggs": { "ms": { "max": { "field": "value" } } } }
                """.formatted(timer) + rateLimited;
    }

    static String query(Range range) {
        // Per bucket: requests, 5xx and the p95 of the slowest endpoint. Actuator calls (health
        // probes) are left out.
        String measures = measures(SERVER_TIMER, false);
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

    /**
     * What one service's traffic is made of over {@code range}: per endpoint (method + path
     * pattern), or for the gateway itself per route - its own endpoint tags are all UNKNOWN for
     * proxied calls, and per route it can also say how much it rate-limited.
     */
    public Mono<EndpointsView> fetchEndpoints(String service, Range range) {
        if (!SERVICE_NAME.matcher(service).matches()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a service name: " + service));
        }
        boolean routes = service.equals(gatewayName);
        return elasticsearch.post()
                .uri("/{index}/_search", index)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(endpointsQuery(service, range, routes))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(5))
                .map(response -> parseEndpoints(response, service, range, routes))
                .onErrorMap(e -> !(e instanceof ResponseStatusException),
                        e -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Metrics are unavailable right now", e));
    }

    static String endpointsQuery(String service, Range range, boolean routes) {
        String timer = routes ? GATEWAY_TIMER : SERVER_TIMER;
        String measures = measures(timer, routes);
        String grouping = routes
                ? """
                  "routes": { "terms": { "field": "routeId.keyword", "size": 100 }, "aggs": { %s } }
                  """.formatted(measures)
                : """
                  "paths": { "terms": { "field": "uri.keyword", "size": 100 },
                    "aggs": { "methods": { "terms": { "field": "method.keyword", "size": 10 }, "aggs": { %s } } } }
                  """.formatted(measures);
        return """
                {
                  "size": 0,
                  "query": { "bool": {
                    "filter": [
                      { "term": { "service.keyword": "%1$s" } },
                      { "terms": { "name": ["%2$s", "%2$s_percentile"] } },
                      { "range": { "@timestamp": { "gte": "now-%3$s" } } } ],
                    "must_not": [ { "prefix": { "uri.keyword": "/actuator" } } ] } },
                  "aggs": { %4$s }
                }
                """.formatted(service, timer, range.key, grouping);
    }

    static EndpointsView parseEndpoints(JsonNode response, String service, Range range, boolean routes) {
        List<EndpointMetrics> rows = new ArrayList<>();
        JsonNode aggs = response.path("aggregations");
        if (routes) {
            for (JsonNode route : aggs.path("routes").path("buckets")) {
                rows.add(row(null, route.path("key").asText(), route, true));
            }
        } else {
            for (JsonNode path : aggs.path("paths").path("buckets")) {
                for (JsonNode method : path.path("methods").path("buckets")) {
                    rows.add(row(method.path("key").asText(), path.path("key").asText(), method, false));
                }
            }
        }
        rows.removeIf(r -> r.requests() == 0);
        rows.sort(Comparator.comparingDouble(EndpointMetrics::requests).reversed());
        return new EndpointsView(service, range.key, routes ? "route" : "endpoint", rows);
    }

    private static EndpointMetrics row(String method, String path, JsonNode bucket, boolean withRateLimited) {
        double requests = requests(bucket);
        double errors = errors(bucket);
        Double rateLimited = withRateLimited ? bucket.path("rateLimited").path("n").path("value").asDouble(0) : null;
        return new EndpointMetrics(method, path, requests, errors, requests > 0 ? errors / requests : null, rateLimited, p95(bucket));
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

    /**
     * @param method      null for a gateway route (all methods)
     * @param path        the endpoint's path pattern, or the gateway route id
     * @param rateLimited 429s the gateway answered itself; null for service endpoints
     * @param p95Ms       the worst p95 seen over the range, null without traffic
     */
    public record EndpointMetrics(String method, String path, double requests, double errors, Double errorRate,
                                  Double rateLimited, Double p95Ms) {
    }

    /** @param grouping "endpoint" (method + path) or "route" (the gateway's own view) */
    public record EndpointsView(String service, String range, String grouping, List<EndpointMetrics> rows) {
    }

    public record MetricsView(String range, long bucketSeconds, List<ServiceMetrics> services) {
    }
}
