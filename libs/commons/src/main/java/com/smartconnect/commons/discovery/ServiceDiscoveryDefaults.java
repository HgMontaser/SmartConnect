package com.smartconnect.commons.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fleet-wide Consul defaults, so plugging a service into SmartConnect takes only the Consul
 * starters, {@code spring.config.import: "vault://,consul:"} and (if it serves traffic)
 * {@code smartconnect.gateway.routes}. Added as the lowest-precedence property source, so any
 * service can override any of it, and before config data loads so the Consul config import
 * already knows where Consul is.
 *
 * <ul>
 *   <li>Authenticates to Consul with the ACL token in {@code CONSUL_TOKEN_FILE}, if set.</li>
 *   <li>Config changes in Consul KV reach the service within a few seconds.</li>
 *   <li>Registers by IP (containers have no stable hostname) under a per-process instance id.</li>
 *   <li>TTL heartbeat carrying actuator health, re-registering if Consul forgets the service
 *       (dev-mode Consul starts empty after a restart); crashed instances are dropped after 1m.</li>
 *   <li>Discovery only returns passing instances, and the load balancer re-reads them every 5s,
 *       so a recreated container is picked up quickly.</li>
 *   <li>Publishes the service's gateway routes as {@link GatewayRouteMetadata} metadata.</li>
 * </ul>
 */
public class ServiceDiscoveryDefaults implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "smartconnectDiscoveryDefaults";

    /**
     * One suffix per JVM. Config refreshes rebuild the environment and run this post-processor
     * again; a fresh value each time would change the instance id and orphan the registration.
     */
    private static final String INSTANCE_SUFFIX = UUID.randomUUID().toString().substring(0, 8);

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.cloud.consul.host", "${CONSUL_HOST:consul}");
        // Consul ACL token (discovery + config) from the file the container mounts.
        String token = readToken(environment.getProperty("CONSUL_TOKEN_FILE"));
        if (token != null) {
            defaults.put("spring.cloud.consul.token", token);
        }
        defaults.put("spring.cloud.consul.port", "${CONSUL_PORT:8500}");
        defaults.put("spring.cloud.consul.config.format", "YAML");
        // The config watch long-polls each KV location in turn; with the default 55s wait a
        // change to config/<app> could queue behind an idle wait on config/application.
        defaults.put("spring.cloud.consul.config.watch.wait-time", "5");

        defaults.put("spring.cloud.consul.discovery.prefer-ip-address", "true");
        defaults.put("spring.cloud.consul.discovery.instance-id", "${spring.application.name}-" + INSTANCE_SUFFIX);
        defaults.put("spring.cloud.consul.discovery.heartbeat.enabled", "true");
        defaults.put("spring.cloud.consul.discovery.heartbeat.ttl", "10s");
        defaults.put("spring.cloud.consul.discovery.heartbeat.reregister-service-on-failure", "true");
        defaults.put("spring.cloud.consul.discovery.health-check-critical-timeout", "1m");
        defaults.put("spring.cloud.consul.discovery.query-passing", "true");
        defaults.put("spring.cloud.loadbalancer.cache.ttl", "5s");

        defaults.put("spring.cloud.consul.discovery.metadata." + GatewayRouteMetadata.ROUTES, "${smartconnect.gateway.routes:}");
        defaults.put("spring.cloud.consul.discovery.metadata." + GatewayRouteMetadata.SCHEME, "${smartconnect.gateway.scheme:http}");
        defaults.put("spring.cloud.consul.discovery.metadata." + GatewayRouteMetadata.TIMEOUT, "${smartconnect.gateway.timeout:}");

        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE, defaults));
    }

    private static String readToken(String file) {
        if (file == null || file.isBlank()) {
            return null;
        }
        try {
            String token = java.nio.file.Files.readString(java.nio.file.Path.of(file)).trim();
            return token.isEmpty() ? null : token;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("CONSUL_TOKEN_FILE is set but unreadable: " + file, e);
        }
    }
}
