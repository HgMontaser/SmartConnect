package com.smartconnect.apigateway.it;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The gateway against real Consul, Vault and Redis, with a stub HTTP service registered in
 * Consul the way any SmartConnect service registers itself (metadata smartconnect-routes).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayRoutingIT {

    @Container
    static final ConsulContainer CONSUL = new ConsulContainer("hashicorp/consul:1.19");

    @Container
    static final VaultContainer<?> VAULT = new VaultContainer<>("hashicorp/vault:1.17").withVaultToken("it-token");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static final KeyPair KEYS = rsaKeys();
    static HttpServer stub;

    @Autowired
    WebTestClient client;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.consul.discovery.register", () -> "false");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.cloud.gateway.httpclient.response-timeout", () -> "2s");
        registry.add("spring.cloud.gateway.httpclient.connect-timeout", () -> "500");
        registry.add("spring.cloud.circuitbreaker.resilience4j.disable-time-limiter", () -> "true");
        registry.add("resilience4j.circuitbreaker.configs.default.sliding-window-size", () -> "4");
        registry.add("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls", () -> "4");
        registry.add("resilience4j.circuitbreaker.configs.default.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state", () -> "30s");
        registry.add("management.tracing.sampling.probability", () -> "0");
    }

    /**
     * Vault and Consul are read while config data loads, before @DynamicPropertySource values
     * exist - so point the same environment variables the containers use at the test containers.
     */
    @BeforeAll
    static void pointConfigServersAtContainers() {
        System.setProperty("VAULT_URI", VAULT.getHttpHostAddress());
        System.setProperty("VAULT_TOKEN", "it-token");
        System.setProperty("CONSUL_HOST", CONSUL.getHost());
        System.setProperty("CONSUL_PORT", String.valueOf(CONSUL.getMappedPort(8500)));
    }

    /** Same as the real stack: Consul KV seeded from infra/consul/config (the repo's source of truth). */
    @BeforeAll
    static void seedConsulConfig() throws Exception {
        for (String name : new String[] {"application", "api-gateway"}) {
            String yaml = java.nio.file.Files.readString(java.nio.file.Path.of("../../infra/consul/config/" + name + ".yml"));
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(consul("/v1/kv/config/" + name + "/data")))
                    .PUT(HttpRequest.BodyPublishers.ofString(yaml)).build(), java.net.http.HttpResponse.BodyHandlers.discarding());
        }
    }

    private static String consul(String path) {
        return "http://" + CONSUL.getHost() + ":" + CONSUL.getMappedPort(8500) + path;
    }

    @BeforeAll
    static void startStubAndRegisterIt() throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/api/stub/ok", exchange -> respond(exchange, 200, "{\"ok\":true}"));
        stub.createContext("/api/stub/slow", exchange -> {
            sleep(Duration.ofSeconds(4));
            respond(exchange, 200, "{\"slow\":true}");
        });
        stub.start();

        String registration = """
                {"ID": "stub-service-1", "Name": "stub-service", "Address": "127.0.0.1", "Port": %d,
                 "Meta": {"smartconnect-routes": "/api/stub/**"}}""".formatted(stub.getAddress().getPort());
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(consul("/v1/agent/service/register")))
                .PUT(HttpRequest.BodyPublishers.ofString(registration)).build(), java.net.http.HttpResponse.BodyHandlers.discarding());
    }

    @AfterAll
    static void cleanUp() {
        if (stub != null) {
            stub.stop(0);
        }
        for (String key : new String[] {"VAULT_URI", "VAULT_TOKEN", "CONSUL_HOST", "CONSUL_PORT"}) {
            System.clearProperty(key);
        }
    }

    @Test
    @Order(1)
    void routesToAServiceRegisteredInConsul() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                client.get().uri("/api/stub/ok").header(HttpHeaders.AUTHORIZATION, bearer()).exchange()
                        .expectStatus().isOk()
                        .expectBody().jsonPath("$.ok").isEqualTo(true));
    }

    @Test
    @Order(2)
    void rejectsMissingTokensAndUnknownPaths() {
        client.get().uri("/api/stub/ok").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/nowhere").header(HttpHeaders.AUTHORIZATION, bearer()).exchange().expectStatus().isNotFound();
    }

    @Test
    @Order(2)
    void platformViewNeedsPlatformReadAndShowsDiscoveredServices() {
        client.get().uri("/api/platform/services").header(HttpHeaders.AUTHORIZATION, bearer()).exchange()
                .expectStatus().isForbidden();
        client.get().uri("/api/platform/services").header(HttpHeaders.AUTHORIZATION, bearer("platform:read")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.name == 'stub-service')].routes[0].paths[0]").isEqualTo("/api/stub/**")
                .jsonPath("$[?(@.name == 'stub-service')].instances[0].status").isEqualTo("PASSING");
    }

    @Test
    @Order(3)
    void aSlowServiceGetsA504InTheStandardErrorShape() {
        client.get().uri("/api/stub/slow").header(HttpHeaders.AUTHORIZATION, bearer()).exchange()
                .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
                .expectBody()
                .jsonPath("$.status").isEqualTo(504)
                .jsonPath("$.path").isEqualTo("/api/stub/slow")
                .jsonPath("$.correlationId").isNotEmpty();
    }

    @Test
    @Order(4)
    void aDeadServiceGets503AndItsCircuitOpens() {
        stub.stop(0);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<?, ?> body = client.get().uri("/api/stub/ok").header(HttpHeaders.AUTHORIZATION, bearer()).exchange()
                    .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                    .expectBody(Map.class).returnResult().getResponseBody();
            assertThat(body).isNotNull();
            assertThat(String.valueOf(body.get("message"))).contains("recover");
        });
    }

    static String bearer(String... permissions) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("it-user")
                    .issuer("http://localhost:8080/realms/smartconnect")
                    .claim("preferred_username", "it-user")
                    .claim("resource_access", Map.of("gateway", Map.of("roles", java.util.List.of(permissions))))
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(KEYS.getPrivate()));
            return "Bearer " + jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair rsaKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Validates the test's own tokens instead of fetching Keycloak's keys. */
    @TestConfiguration
    static class TestTokens {
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        }
    }
}
