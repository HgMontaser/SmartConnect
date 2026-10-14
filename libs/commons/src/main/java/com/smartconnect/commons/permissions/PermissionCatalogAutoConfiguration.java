package com.smartconnect.commons.permissions;

import com.smartconnect.commons.events.PermissionCatalogEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * Plugs a business service into SmartConnect's permission model: set
 * {@code smartconnect.permissions.service} and its catalog is verified and published at
 * startup. Uses its own small producer (String key, JSON value, no type headers) so it works
 * regardless of how the service configures Kafka for its own messages.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "smartconnect.permissions", name = "service")
@EnableConfigurationProperties(PermissionCatalogProperties.class)
public class PermissionCatalogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PermissionCatalogProvider permissionCatalogProvider(PermissionCatalogProperties properties) {
        return new PropertiesPermissionCatalogProvider(properties);
    }

    @Bean
    public NewTopic permissionCatalogTopic() {
        return PermissionCatalogTopic.newTopic();
    }

    @Bean
    public PermissionCatalogPublisher permissionCatalogPublisher(
            PermissionCatalogProperties properties, PermissionCatalogProvider provider,
            KafkaProperties kafkaProperties, ApplicationContext context) {
        Map<String, Object> config = kafkaProperties.buildProducerProperties(null);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        KafkaTemplate<String, PermissionCatalogEvent> template =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
        template.setObservationEnabled(true);

        return new PermissionCatalogPublisher(properties.getService(), provider, template,
                new DeclaredPermissionsVerifier(context), properties.isVerify());
    }
}
