package com.smartconnect.commons.correlation;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

/**
 * Forwards the correlation id carried in the Reactor {@code Context} (written by
 * {@link CorrelationIdWebFilter}) onto any outbound WebClient call. Context propagation
 * through a reactive chain is per-subscription and thread-safe, unlike MDC, so this is the
 * correct mechanism on the reactive stack.
 */
public final class CorrelationIdExchangeFilterFunction {

    private CorrelationIdExchangeFilterFunction() {
    }

    public static ExchangeFilterFunction propagateHeader() {
        return (request, next) -> Mono.deferContextual(contextView -> {
            String correlationId = contextView.getOrDefault(CorrelationConstants.REACTOR_CONTEXT_KEY, null);
            if (correlationId == null) {
                return next.exchange(request);
            }
            ClientRequest mutated = ClientRequest.from(request)
                    .header(CorrelationConstants.HEADER_NAME, correlationId)
                    .build();
            return next.exchange(mutated);
        });
    }
}
