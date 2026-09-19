package com.smartconnect.apigateway.logging;

import com.smartconnect.commons.correlation.CorrelationConstants;
import com.smartconnect.commons.correlation.CorrelationIdWebFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Logs one line per proxied request with the correlation id stamped by commons' reactive
 * correlation filter, so a request can be traced through this gateway's own logs in Kibana -
 * not just through the services behind it. MDC is set/cleared synchronously around a single
 * log statement (safe on the reactive stack: no reactor operator boundary is crossed between
 * the put and the log call), never left dangling across an async boundary.
 */
@Component
public class CorrelationLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationLoggingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = CorrelationIdWebFilter.currentCorrelationId(exchange);
        ServerHttpRequest request = exchange.getRequest();
        if (correlationId != null) {
            MDC.put(CorrelationConstants.MDC_KEY, correlationId);
        }
        try {
            log.info("routing {} {}", request.getMethod(), request.getPath().value());
        } finally {
            MDC.remove(CorrelationConstants.MDC_KEY);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
