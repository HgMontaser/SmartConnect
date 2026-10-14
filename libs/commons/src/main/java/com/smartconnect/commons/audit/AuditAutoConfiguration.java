package com.smartconnect.commons.audit;

import com.smartconnect.commons.messaging.EventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Turns on {@link AuditInterceptor} for every servlet service (opt out with
 * {@code smartconnect.audit.enabled=false}). Publishes audit events when the service has
 * Kafka (an {@link EventPublisher}); otherwise only logs them.
 */
@AutoConfiguration(afterName = "com.smartconnect.commons.messaging.MessagingAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnProperty(prefix = "smartconnect.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    public AuditInterceptor auditInterceptor(@Value("${spring.application.name:service}") String service,
                                             ObjectProvider<EventPublisher> events) {
        return new AuditInterceptor(service, events.getIfAvailable());
    }

    @Bean
    public WebMvcConfigurer auditWebMvcConfigurer(AuditInterceptor auditInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(auditInterceptor).addPathPatterns("/api/**");
            }
        };
    }
}
