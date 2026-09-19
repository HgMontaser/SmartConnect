package com.smartconnect.commons.correlation;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Forwards the current correlation id (read from MDC) onto any outbound RestTemplate call.
 * Wired automatically onto every RestTemplateBuilder via {@code RestTemplateCustomizer}
 * (see {@code ServletCorrelationIdAutoConfiguration}).
 */
public class CorrelationIdClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get(CorrelationConstants.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            request.getHeaders().set(CorrelationConstants.HEADER_NAME, correlationId);
        }
        return execution.execute(request, body);
    }
}
