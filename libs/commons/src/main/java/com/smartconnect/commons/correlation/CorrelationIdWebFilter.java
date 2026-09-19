package com.smartconnect.commons.correlation;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive equivalent of {@link CorrelationIdServletFilter} for api-gateway (WebFlux).
 *
 * <p>Deliberately does NOT rely on SLF4J MDC: MDC is a ThreadLocal and Netty/Reactor hop
 * across threads per request, so a naive MDC.put/remove here would leak ids between
 * concurrent requests. Instead the id is stored as a {@link ServerWebExchange} attribute
 * (safe, one exchange per request) and written into the Reactor {@code Context} so
 * downstream reactive chains (e.g. the WebClient filter that forwards the header on
 * outbound calls) can read it correctly regardless of which thread they run on.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(CorrelationConstants.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        final String finalCorrelationId = correlationId;

        exchange.getAttributes().put(CorrelationConstants.EXCHANGE_ATTRIBUTE, finalCorrelationId);
        exchange.getResponse().getHeaders().set(CorrelationConstants.HEADER_NAME, finalCorrelationId);

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(CorrelationConstants.REACTOR_CONTEXT_KEY, finalCorrelationId));
    }

    /** Reads the correlation id stamped by this filter, for use in other gateway filters/logging. */
    public static String currentCorrelationId(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(CorrelationConstants.EXCHANGE_ATTRIBUTE);
        return value != null ? value.toString() : null;
    }
}
