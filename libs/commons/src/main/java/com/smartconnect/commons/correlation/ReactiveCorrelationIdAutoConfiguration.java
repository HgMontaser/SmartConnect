package com.smartconnect.commons.correlation;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.WebFilter;

/**
 * Wires the correlation-id filter + outbound WebClient propagation for api-gateway (WebFlux).
 * Only activates when the reactive web stack is actually on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass({WebFilter.class, DispatcherHandler.class})
public class ReactiveCorrelationIdAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdWebFilter correlationIdWebFilter() {
        return new CorrelationIdWebFilter();
    }

    @Bean
    public WebClientCustomizer correlationIdWebClientCustomizer() {
        return builder -> builder.filter(CorrelationIdExchangeFilterFunction.propagateHeader());
    }
}
