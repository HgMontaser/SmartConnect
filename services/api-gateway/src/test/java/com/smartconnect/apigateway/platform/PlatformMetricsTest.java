package com.smartconnect.apigateway.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformMetricsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void queryIsValidJsonWithTheRangesBucketSize() throws Exception {
        JsonNode query = json.readTree(PlatformMetrics.query(PlatformMetrics.Range.H1));

        JsonNode histogram = query.at("/aggs/services/aggs/series/date_histogram");
        assertThat(histogram.path("fixed_interval").asText()).isEqualTo("60s");
        assertThat(histogram.at("/extended_bounds/min").asText()).isEqualTo("now-1h");
        assertThat(query.at("/aggs/services/aggs/recent/aggs/p95/aggs/ms/max/field").asText()).isEqualTo("value");
    }

    @Test
    void parsesSeriesAndTheRecentSummary() throws Exception {
        JsonNode response = json.readTree("""
                { "aggregations": { "services": { "buckets": [ {
                    "key": "user-service",
                    "series": { "buckets": [
                      { "key": 1000, "requests": { "n": { "value": 30 } }, "errors": { "n": { "value": 3 } }, "p95": { "ms": { "value": 120.5 } } },
                      { "key": 61000, "requests": { "n": { "value": 0 } }, "errors": { "n": { "value": 0 } }, "p95": { "ms": { "value": 0.0 } } } ] },
                    "recent": { "requests": { "n": { "value": 500 } }, "errors": { "n": { "value": 5 } }, "p95": { "ms": { "value": 140.0 } } } } ] } } }
                """);

        PlatformMetrics.MetricsView view = PlatformMetrics.parse(response, PlatformMetrics.Range.H1);

        assertThat(view.range()).isEqualTo("1h");
        assertThat(view.bucketSeconds()).isEqualTo(60);
        PlatformMetrics.ServiceMetrics service = view.services().get(0);
        assertThat(service.service()).isEqualTo("user-service");
        assertThat(service.requestsPerMinute()).isEqualTo(100.0);
        assertThat(service.errorRate()).isEqualTo(0.01);
        assertThat(service.p95Ms()).isEqualTo(140.0);
        assertThat(service.series()).containsExactly(
                new PlatformMetrics.Point(1000, 30, 3, 120.5),
                new PlatformMetrics.Point(61000, 0, 0, null));
    }

    @Test
    void noRecentTrafficMeansNoRateAndNoLatency() throws Exception {
        JsonNode response = json.readTree("""
                { "aggregations": { "services": { "buckets": [ {
                    "key": "notification-service", "series": { "buckets": [] },
                    "recent": { "requests": { "n": { "value": 0 } }, "errors": { "n": { "value": 0 } }, "p95": { "ms": { "value": null } } } } ] } } }
                """);

        PlatformMetrics.ServiceMetrics service = PlatformMetrics.parse(response, PlatformMetrics.Range.M15).services().get(0);

        assertThat(service.requestsPerMinute()).isZero();
        assertThat(service.errorRate()).isNull();
        assertThat(service.p95Ms()).isNull();
    }

    @Test
    void unknownRangeIsABadRequest() {
        assertThatThrownBy(() -> PlatformMetrics.Range.of("7d"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("15m, 1h, 6h, 24h");
    }
}
