package com.smartconnect.commons.correlation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void aGeneratedIdIsForwardedDownstreamAndReturnedToTheCaller() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, e -> {
            forwarded.set(e);
            return Mono.empty();
        }).block();

        String generated = exchange.getResponse().getHeaders().getFirst(CorrelationConstants.HEADER_NAME);
        assertThat(generated).isNotBlank();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationConstants.HEADER_NAME)).isEqualTo(generated);
        assertThat(CorrelationIdWebFilter.currentCorrelationId(forwarded.get())).isEqualTo(generated);
    }

    @Test
    void anIncomingIdIsKept() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").header(CorrelationConstants.HEADER_NAME, "abc-123"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, e -> {
            forwarded.set(e);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationConstants.HEADER_NAME)).isEqualTo("abc-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationConstants.HEADER_NAME)).isEqualTo("abc-123");
    }
}
