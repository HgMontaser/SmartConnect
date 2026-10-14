package com.smartconnect.commons.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceDiscoveryDefaultsTest {

    @Test
    void instanceIdIsStableAcrossEnvironmentRebuilds() {
        // A config refresh builds a new environment and runs the post-processor again.
        assertThat(instanceIdAfterPostProcessing()).isEqualTo(instanceIdAfterPostProcessing());
    }

    @Test
    void serviceSettingsOverrideDefaults() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getSystemProperties().put("spring.cloud.consul.host", "elsewhere");
        new ServiceDiscoveryDefaults().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.cloud.consul.host")).isEqualTo("elsewhere");
        assertThat(environment.getProperty("spring.cloud.consul.discovery.prefer-ip-address")).isEqualTo("true");
    }

    @Test
    void publishesGatewayRoutesAsMetadata() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getSystemProperties().put("smartconnect.gateway.routes", "/api/x/**");
        new ServiceDiscoveryDefaults().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.cloud.consul.discovery.metadata." + GatewayRouteMetadata.ROUTES)).isEqualTo("/api/x/**");
        assertThat(environment.getProperty("spring.cloud.consul.discovery.metadata." + GatewayRouteMetadata.SCHEME)).isEqualTo("http");
    }

    private static String instanceIdAfterPostProcessing() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getSystemProperties().put("spring.application.name", "svc");
        new ServiceDiscoveryDefaults().postProcessEnvironment(environment, null);
        return environment.getProperty("spring.cloud.consul.discovery.instance-id");
    }
}
