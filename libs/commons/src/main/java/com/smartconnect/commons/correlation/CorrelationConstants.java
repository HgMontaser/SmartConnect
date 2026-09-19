package com.smartconnect.commons.correlation;

/**
 * Well-known names used to propagate a correlation id across the whole SmartConnect
 * request chain: gateway -&gt; user-service -&gt; Kafka -&gt; notification-service.
 */
public final class CorrelationConstants {

    /** Header carried on every inbound/outbound HTTP hop and stamped on Kafka records. */
    public static final String HEADER_NAME = "X-Correlation-Id";

    /** SLF4J MDC key used by servlet-stack services so every log line carries the id. */
    public static final String MDC_KEY = "correlationId";

    /** Reactor Context key used by the reactive stack (api-gateway), where MDC is unsafe. */
    public static final String REACTOR_CONTEXT_KEY = "correlationId";

    /** ServerWebExchange attribute key so reactive filters can read the id without MDC. */
    public static final String EXCHANGE_ATTRIBUTE = "smartconnect.correlationId";

    private CorrelationConstants() {
    }
}
