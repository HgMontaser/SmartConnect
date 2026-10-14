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
        props(environment).put("spring.cloud.consul.host", "elsewhere");
        new ServiceDiscoveryDefaults().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.cloud.consul.host")).isEqualTo("elsewhere");
        assertThat(environment.getProperty("spring.cloud.consul.discovery.prefer-ip-address")).isEqualTo("true");
    }

    @Test
    void publishesGatewayRoutesAsMetadata() {
        StandardEnvironment environment = new StandardEnvironment();
        props(environment).put("smartconnect.gateway.routes", "/api/x/**");
        new ServiceDiscoveryDefaults().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.cloud.consul.discovery.metadata." + GatewayRouteMetadata.ROUTES)).isEqualTo("/api/x/**");
        assertThat(environment.getProperty("spring.cloud.consul.discovery.metadata." + GatewayRouteMetadata.SCHEME)).isEqualTo("http");
    }

    @Test
    void readsTheConsulTokenFromTheMountedFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = java.nio.file.Files.writeString(dir.resolve("consul-token"), "  abc-123\n");
        StandardEnvironment environment = new StandardEnvironment();
        props(environment).put("CONSUL_TOKEN_FILE", file.toString());
        new ServiceDiscoveryDefaults().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.cloud.consul.token")).isEqualTo("abc-123");
    }

    @Test
    void logsInToVaultWithTheMountedAppRole(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        props(environment).put("VAULT_ROLE_ID_FILE", java.nio.file.Files.writeString(dir.resolve("role"), "role-1\n").toString());
        props(environment).put("VAULT_SECRET_ID_FILE", java.nio.file.Files.writeString(dir.resolve("secret"), "secret-1").toString());
        new ServiceDiscoveryDefaults().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.cloud.vault.authentication")).isEqualTo("APPROLE");
        assertThat(environment.getProperty("spring.cloud.vault.app-role.role-id")).isEqualTo("role-1");
        assertThat(environment.getProperty("spring.cloud.vault.app-role.secret-id")).isEqualTo("secret-1");
    }

    @Test
    void fallsBackToAVaultTokenWithoutAppRoleFiles() {
        StandardEnvironment environment = new StandardEnvironment();
        props(environment).put("VAULT_TOKEN", "t-1");
        new ServiceDiscoveryDefaults().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.cloud.vault.authentication")).isEqualTo("TOKEN");
        assertThat(environment.getProperty("spring.cloud.vault.token")).isEqualTo("t-1");
    }

    @Test
    void noTokenFileMeansNoToken() {
        StandardEnvironment environment = new StandardEnvironment();
        new ServiceDiscoveryDefaults().postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.cloud.consul.token")).isNull();
    }

    /** An isolated property source - getSystemProperties() would mutate the real JVM properties. */
    private static java.util.Map<String, Object> props(StandardEnvironment environment) {
        var source = environment.getPropertySources().get("test");
        if (source == null) {
            source = new org.springframework.core.env.MapPropertySource("test", new java.util.HashMap<>());
            environment.getPropertySources().addFirst(source);
        }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) source.getSource();
        return map;
    }

    private static String instanceIdAfterPostProcessing() {
        StandardEnvironment environment = new StandardEnvironment();
        props(environment).put("spring.application.name", "svc");
        new ServiceDiscoveryDefaults().postProcessEnvironment(environment, null);
        return environment.getProperty("spring.cloud.consul.discovery.instance-id");
    }
}
