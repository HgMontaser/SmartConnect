package com.smartconnect.commons.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Common Actuator/Micrometer tags applied to every metric this service emits, so every
 * service's data is distinguishable once it lands in the shared Elastic stack:
 * {@code service} (from {@code spring.application.name}) and {@code environment}
 * (from {@code smartconnect.environment}, default {@code local}).
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class ObservabilityAutoConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer(
            @Value("${spring.application.name:smartconnect-service}") String serviceName,
            @Value("${smartconnect.environment:local}") String environment) {
        return registry -> registry.config().commonTags("service", serviceName, "environment", environment);
    }
}
