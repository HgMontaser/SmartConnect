package com.smartconnect.commons.kafka;

import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Turns on tracing for a {@link KafkaTemplate} that commons builds itself instead of declaring
 * as a bean. {@code setObservationEnabled(true)} alone does nothing for such a template: it only
 * picks up the service's {@code ObservationRegistry} in {@code afterSingletonsInstantiated()},
 * which Spring calls on beans only - otherwise it sends with a no-op registry, writes no
 * {@code traceparent} header, and every consumer starts a new trace.
 */
public final class ObservedKafkaTemplates {

    private ObservedKafkaTemplates() {
    }

    public static <K, V> KafkaTemplate<K, V> observed(KafkaTemplate<K, V> template, ApplicationContext context) {
        template.setObservationEnabled(true);
        template.setApplicationContext(context);
        template.afterSingletonsInstantiated();
        return template;
    }
}
