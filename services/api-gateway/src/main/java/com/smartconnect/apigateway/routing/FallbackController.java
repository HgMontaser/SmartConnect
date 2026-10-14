package com.smartconnect.apigateway.routing;

import com.smartconnect.commons.correlation.CorrelationConstants;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;

/**
 * Where a route's circuit breaker sends a failed call. Answers in the same JSON shape as every
 * service's errors, with 504 when the service was too slow and 503 otherwise (down, unreachable,
 * or its breaker is open and calls are being refused to let it recover).
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    @RequestMapping(path = "/fallback/unavailable")
    public ResponseEntity<Map<String, Object>> unavailable(ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        HttpStatus status;
        String message;
        log.info("fallback for {}: {}", originalPath(exchange), cause == null ? "no cause" : cause.toString());
        if (isTimeout(cause)) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            message = "The service did not respond in time";
        } else if (cause instanceof CallNotPermittedException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "The service is failing and is being given time to recover - try again shortly";
        } else {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "The service is temporarily unavailable";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", originalPath(exchange));
        // The gateway stores the id it assigned on the exchange; the header only exists if the client sent one.
        Object correlationId = exchange.getAttribute(CorrelationConstants.EXCHANGE_ATTRIBUTE);
        body.put("correlationId", correlationId != null ? correlationId
                : exchange.getRequest().getHeaders().getFirst(CorrelationConstants.HEADER_NAME));
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Timeouts reach here in several shapes (the gateway's response timeout, Netty's read
     * timeout, or a 504 status the breaker turned into an exception), possibly wrapped.
     */
    private static boolean isTimeout(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t.getClass().getName().contains("Timeout") || String.valueOf(t.getMessage()).contains("504")) {
                return true;
            }
        }
        return false;
    }

    private static String originalPath(ServerWebExchange exchange) {
        LinkedHashSet<URI> urls = exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        return urls == null || urls.isEmpty() ? exchange.getRequest().getPath().value() : urls.iterator().next().getPath();
    }
}
