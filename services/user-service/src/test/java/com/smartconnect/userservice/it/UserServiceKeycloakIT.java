package com.smartconnect.userservice.it;

import com.fasterxml.jackson.databind.JsonNode;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.vault.VaultContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * user-service against a real Keycloak (the repo's realm export + infra/keycloak/init.sh), Kafka,
 * Consul and Vault, called with real Keycloak tokens.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserServiceKeycloakIT {

    static final String SECRET = "it-user-service-secret";

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:25.0")
            .withRealmImportFile("realm-export.json")
            .withCopyFileToContainer(MountableFile.forHostPath("../../infra/keycloak/init.sh"), "/init.sh");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    @Container
    static final ConsulContainer CONSUL = new ConsulContainer("hashicorp/consul:1.19");

    @Container
    static final VaultContainer<?> VAULT = new VaultContainer<>("hashicorp/vault:1.17").withVaultToken("it-token");

    @LocalServerPort
    int port;

    @BeforeAll
    static void prepareStack() throws Exception {
        // The same one-shot job compose runs: creates the scoped user-service service account.
        var result = KEYCLOAK.execInContainer("sh", "-c",
                "KEYCLOAK_URL=http://localhost:8080 KEYCLOAK_ADMIN=admin KEYCLOAK_ADMIN_PASSWORD=admin "
                        + "USER_SERVICE_CLIENT_SECRET=" + SECRET + " sh /init.sh");
        assertThat(result.getExitCode()).as(result.getStdout() + result.getStderr()).isZero();

        // Config servers are read before @DynamicPropertySource applies - use the env-var hooks.
        System.setProperty("VAULT_URI", VAULT.getHttpHostAddress());
        System.setProperty("VAULT_TOKEN", "it-token");
        System.setProperty("CONSUL_HOST", CONSUL.getHost());
        System.setProperty("CONSUL_PORT", String.valueOf(CONSUL.getMappedPort(8500)));
        String yaml = Files.readString(Path.of("../../infra/consul/config/application.yml"));
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                        "http://" + CONSUL.getHost() + ":" + CONSUL.getMappedPort(8500) + "/v1/kv/config/application/data"))
                .PUT(HttpRequest.BodyPublishers.ofString(yaml)).build(), HttpResponse.BodyHandlers.discarding());
    }

    @AfterAll
    static void cleanUp() {
        for (String key : new String[] {"VAULT_URI", "VAULT_TOKEN", "CONSUL_HOST", "CONSUL_PORT"}) {
            System.clearProperty(key);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String realm = KEYCLOAK.getAuthServerUrl() + "/realms/smartconnect";
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> realm);
        registry.add("smartconnect.commons.security.browser-issuer-uri", () -> realm);
        registry.add("keycloak.admin.server-url", KEYCLOAK::getAuthServerUrl);
        registry.add("keycloak.admin.realm", () -> "smartconnect");
        registry.add("keycloak.admin.client-id", () -> "user-service");
        registry.add("keycloak.admin.client-secret", () -> SECRET);
        registry.add("keycloak.target-realm", () -> "smartconnect");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("management.tracing.sampling.probability", () -> "0");
    }

    @Test
    void bootstrapSyncsTheSystemCatalogAndAdminHoldsEverything() {
        JsonNode permissions = api(admin()).get().uri("/api/permissions").retrieve().body(JsonNode.class);
        List<String> system = names(permissions, true);
        assertThat(system).contains("users:read", "users:write", "users:delete", "roles:read", "roles:write",
                "permissions:read", "permissions:write");

        JsonNode adminRole = api(admin()).get().uri("/api/roles/admin").retrieve().body(JsonNode.class);
        assertThat(adminRole.get("system").asBoolean()).isTrue();
        assertThat(StreamSupport.stream(adminRole.get("permissions").spliterator(), false).map(JsonNode::asText))
                .containsAll(system);
    }

    @Test
    void rolesCrudKeepsAdminLockedAndRejectedUpdatesChangeNothing() {
        String token = admin();
        String role = "it-role-" + suffix();
        assertThat(status(api(token).post().uri("/api/roles").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", role, "description", "IT", "permissions", List.of("users:read"))))).isEqualTo(201);
        assertThat(status(api(token).put().uri("/api/roles/" + role).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("description", "IT 2", "permissions", List.of("users:read", "roles:read"))))).isEqualTo(200);

        String adminDescription = api(token).get().uri("/api/roles/admin").retrieve().body(JsonNode.class).get("description").asText();
        assertThat(status(api(token).put().uri("/api/roles/admin").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("description", "SHOULD NOT STICK", "permissions", List.of("users:read"))))).isEqualTo(400);
        assertThat(api(token).get().uri("/api/roles/admin").retrieve().body(JsonNode.class).get("description").asText())
                .isEqualTo(adminDescription);

        assertThat(status(api(token).delete().uri("/api/roles/admin"))).isEqualTo(409);
        assertThat(status(api(token).delete().uri("/api/roles/" + role))).isEqualTo(204);
    }

    @Test
    void anUnknownRoleCreatesNoUser() {
        String token = admin();
        String username = "it-ghost-" + suffix();
        assertThat(status(api(token).post().uri("/api/users").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "email", username + "@it.local", "password", "Passw0rd!x",
                        "realmRoles", List.of("no-such-role"))))).isEqualTo(400);
        JsonNode users = api(token).get().uri("/api/users").retrieve().body(JsonNode.class);
        assertThat(StreamSupport.stream(users.spliterator(), false).map(u -> u.get("username").asText())).doesNotContain(username);
    }

    @Test
    void aServiceCatalogFromKafkaIsSyncedWithOwnershipAndOrphaning() throws Exception {
        String service = "it-svc-" + suffix();
        String permission = service.replace("it-svc-", "itsvc") + ":read";
        publishCatalog(service, "{\"service\":\"" + service + "\",\"permissions\":{\"" + permission + "\":\"IT read\"},\"publishedAt\":\"2026-01-01T00:00:00Z\"}");

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            JsonNode p = permission(permission);
            assertThat(p).isNotNull();
            assertThat(p.get("owner").asText()).isEqualTo(service);
            assertThat(p.get("roles").toString()).contains("admin");
        });
        // Service-owned: read-only here.
        assertThat(status(api(admin()).put().uri("/api/permissions/" + permission).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("description", "x")))).isEqualTo(409);

        publishCatalog(service, null); // tombstone: service removed
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(permission(permission).get("orphaned").asBoolean()).isTrue());
        assertThat(status(api(admin()).delete().uri("/api/permissions/" + permission))).isEqualTo(204);
    }

    @Test
    void endpointsRequireTheirPermission() {
        String username = "it-plain-" + suffix();
        assertThat(status(api(admin()).post().uri("/api/users").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "email", username + "@it.local", "firstName", "It", "lastName", "Plain",
                        "password", "Passw0rd!x", "realmRoles", List.of("user"))))).isEqualTo(201);

        String plain = token(username, "Passw0rd!x");
        assertThat(status(api(plain).get().uri("/api/users"))).isEqualTo(403);
        assertThat(status(api(plain).get().uri("/api/roles"))).isEqualTo(403);
        RestClient anonymous = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { }).build();
        assertThat(status(anonymous.get().uri("/api/users"))).isEqualTo(401);
    }

    // --- helpers

    private RestClient api(String token) {
        return RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                .build();
    }

    private static int status(RestClient.RequestHeadersSpec<?> request) {
        return request.retrieve().toBodilessEntity().getStatusCode().value();
    }

    private JsonNode permission(String name) {
        JsonNode all = api(admin()).get().uri("/api/permissions").retrieve().body(JsonNode.class);
        return StreamSupport.stream(all.spliterator(), false).filter(p -> p.get("name").asText().equals(name)).findFirst().orElse(null);
    }

    private static List<String> names(JsonNode permissions, boolean system) {
        return StreamSupport.stream(permissions.spliterator(), false)
                .filter(p -> p.get("system").asBoolean() == system).map(p -> p.get("name").asText()).toList();
    }

    private static String admin() {
        return token("admin.seed", "SmartConnect123!");
    }

    private static String token(String username, String password) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "login-app");
        form.add("username", username);
        form.add("password", password);
        JsonNode response = RestClient.create().post()
                .uri(KEYCLOAK.getAuthServerUrl() + "/realms/smartconnect/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(JsonNode.class);
        return response.get("access_token").asText();
    }

    private static void publishCatalog(String service, String json) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>("permission-catalog", service, json)).get();
        }
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
