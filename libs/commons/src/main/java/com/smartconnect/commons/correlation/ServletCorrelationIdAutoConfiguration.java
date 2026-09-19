package com.smartconnect.commons.correlation;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Wires the correlation-id filter + outbound RestTemplate propagation for any service on the
 * servlet (Spring MVC) stack. Only activates when Spring MVC is actually on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass({OncePerRequestFilter.class, DispatcherServlet.class})
public class ServletCorrelationIdAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdServletFilter correlationIdServletFilter() {
        return new CorrelationIdServletFilter();
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdServletFilter> correlationIdFilterRegistration(
            CorrelationIdServletFilter filter) {
        FilterRegistrationBean<CorrelationIdServletFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("correlationIdServletFilter");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdClientHttpRequestInterceptor correlationIdClientHttpRequestInterceptor() {
        return new CorrelationIdClientHttpRequestInterceptor();
    }

    @Bean
    public RestTemplateCustomizer correlationIdRestTemplateCustomizer(
            CorrelationIdClientHttpRequestInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }
}
