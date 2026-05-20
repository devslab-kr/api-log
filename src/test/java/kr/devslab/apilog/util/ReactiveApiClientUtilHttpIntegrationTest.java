package kr.devslab.apilog.util;

import com.fasterxml.jackson.databind.JsonNode;
import kr.devslab.apilog.model.ApiLogEntity;
import kr.devslab.apilog.model.dto.ApiRequest;
import kr.devslab.apilog.model.dto.ApiResponse;
import kr.devslab.apilog.repository.ApiLogRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end HTTP integration for {@link ReactiveApiClientUtil}.
 *
 * <p>Drives real HTTP through an in-process {@link MockWebServer}, lets the
 * async listener drain into a real PostgreSQL 15 container (Testcontainers),
 * then asserts on the {@code api_log} rows — same shape of guarantees as the
 * blocking {@link RestApiClientUtilHttpIntegrationTest}, but exercising the
 * {@link WebClient} code path so reactive callers also pay for what they get.
 */
@SpringBootTest
@Testcontainers
class ReactiveApiClientUtilHttpIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("apilog_reactive_it")
            .withUsername("test")
            .withPassword("test");

    static final MockWebServer mockServer;

    static {
        mockServer = new MockWebServer();
        try {
            mockServer.start();
        } catch (IOException e) {
            throw new RuntimeException("Could not start MockWebServer", e);
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("api.log.schema.management", () -> "builtin");
    }

    @AfterAll
    static void stopMockServer() throws IOException {
        mockServer.shutdown();
    }

    @TestConfiguration
    static class ReactiveTestConfig {
        /**
         * Override {@link WebClient.Builder} so the test instance of
         * {@link ReactiveApiClientUtil} routes traffic to {@link #mockServer}
         * instead of the real network. Spring Boot's WebClientCustomizers are
         * still applied because {@code @Primary} doesn't swap them.
         */
        @Bean
        @Primary
        WebClient.Builder testWebClientBuilder() {
            return WebClient.builder().baseUrl(mockServer.url("/").toString());
        }
    }

    @Autowired
    ReactiveApiClientUtil api;

    @Autowired
    ApiLogRepository repository;

    @BeforeEach
    void clearLog() throws InterruptedException {
        repository.deleteAll();
        while (mockServer.getRequestCount() > 0 && mockServer.takeRequest(1, TimeUnit.MILLISECONDS) != null) {
            // discard
        }
    }

    // ------------------------------------------------------------------ //
    // Success path — status code propagation                              //
    // ------------------------------------------------------------------ //

    @Test
    void get_2xx_propagatesActualStatusCodeIntoApiLog() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":1,\"name\":\"Ada\"}"));

        StepVerifier.create(api.get("/users/1"))
                .assertNext(resp -> {
                    assertThat(resp.getStatusCode()).isEqualTo(201);
                    assertThat(resp.getData()).contains("\"name\":\"Ada\"");
                })
                .verifyComplete();

        ApiLogEntity successRow = waitForRow("SUCCESS");
        assertThat(successRow.getStatusCode()).isEqualTo(201);
        assertThat(successRow.getResponse().get("id").asInt()).isEqualTo(1);
    }

    @Test
    void postTyped_deserializesResponseAndLogsRows() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"name\":\"Ada\",\"email\":\"ada@example.com\"}"));

        Mono<TestUser> mono = api.postTyped("/users", new TestUser("Ada", "ada@example.com"), TestUser.class);

        StepVerifier.create(mono)
                .assertNext(user -> {
                    assertThat(user.name()).isEqualTo("Ada");
                    assertThat(user.email()).isEqualTo("ada@example.com");
                })
                .verifyComplete();

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<ApiLogEntity> rows = repository.findAll();
            assertThat(rows).hasSize(2);
            assertThat(rows.stream().map(ApiLogEntity::getEventType))
                    .containsExactlyInAnyOrder("INITIATED", "SUCCESS");
            assertThat(rows.stream().map(ApiLogEntity::getRequestId).distinct()).hasSize(1);
        });
    }

    // ------------------------------------------------------------------ //
    // Error path                                                            //
    // ------------------------------------------------------------------ //

    @Test
    void clientError_4xx_capturesStatusCodeAndStructuredErrorMessage() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"missing\"}"));

        StepVerifier.create(api.get("/users/999"))
                .expectErrorSatisfies(throwable ->
                        assertThat(throwable).isInstanceOf(WebClientResponseException.class))
                .verify();

        ApiLogEntity errorRow = waitForRow("ERROR");
        assertThat(errorRow.getStatusCode()).isEqualTo(404);

        JsonNode err = errorRow.getErrorMessage();
        assertThat(err.get("type").asText()).contains("WebClientResponseException");
        assertThat(err.has("message")).isTrue();
        // WebClientResponseException carries the upstream body via getResponseBodyAsString.
        assertThat(err.get("responseBody").asText())
                .isEqualTo("{\"error\":\"missing\"}");
    }

    // ------------------------------------------------------------------ //
    // send() — caller-provided request_id correlates attempts              //
    // ------------------------------------------------------------------ //

    @Test
    void send_withCustomRequestId_correlatesAttemptsForRetryTimeline() {
        String correlationId = "rx-retry-" + UUID.randomUUID();
        mockServer.enqueue(new MockResponse().setResponseCode(503));
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        ApiRequest req = ApiRequest.builder()
                .endpoint("/charges")
                .payload("{}")
                .requestId(correlationId)
                .build();

        // Mono.onErrorResume gives us the same caller-driven retry shape used
        // in the docs — a Resilience4j / .retry() style chain would behave the
        // same way as long as the ApiRequest (and its requestId) is reused.
        ApiResponse finalResponse = api.send(HttpMethod.POST, req)
                .onErrorResume(throwable -> api.send(HttpMethod.POST, req))
                .block();

        assertThat(finalResponse).isNotNull();
        assertThat(finalResponse.getStatusCode()).isEqualTo(200);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<ApiLogEntity> rows = repository.findAll().stream()
                    .filter(r -> correlationId.equals(r.getRequestId()))
                    .toList();
            // 2× INITIATED + 1 ERROR + 1 SUCCESS = 4 rows, all sharing requestId.
            assertThat(rows).hasSize(4);
            assertThat(rows.stream().map(ApiLogEntity::getEventType))
                    .filteredOn(t -> t.equals("INITIATED")).hasSize(2);
            assertThat(rows.stream().map(ApiLogEntity::getEventType))
                    .filteredOn(t -> t.equals("ERROR")).hasSize(1);
            assertThat(rows.stream().map(ApiLogEntity::getEventType))
                    .filteredOn(t -> t.equals("SUCCESS")).hasSize(1);
        });
    }

    // ------------------------------------------------------------------ //
    // Verb coverage                                                        //
    // ------------------------------------------------------------------ //

    @Test
    void put_routesPutAndLogs() throws InterruptedException {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        api.put("/users/1", "{\"name\":\"x\"}").block();

        assertThat(mockServer.takeRequest().getMethod()).isEqualTo("PUT");
        waitForRow("SUCCESS");
    }

    @Test
    void delete_routesDeleteAndLogs204() throws InterruptedException {
        mockServer.enqueue(new MockResponse().setResponseCode(204));

        api.delete("/users/1").block();

        assertThat(mockServer.takeRequest().getMethod()).isEqualTo("DELETE");
        ApiLogEntity success = waitForRow("SUCCESS");
        assertThat(success.getStatusCode()).isEqualTo(204);
    }

    @Test
    void patch_routesPatchAndLogs() throws InterruptedException {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        api.patch("/users/1", "{\"email\":\"x@y\"}").block();

        assertThat(mockServer.takeRequest().getMethod()).isEqualTo("PATCH");
        waitForRow("SUCCESS");
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                              //
    // ------------------------------------------------------------------ //

    private ApiLogEntity waitForRow(String eventType) {
        return Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(50))
                .until(
                        () -> repository.findAll().stream()
                                .filter(r -> eventType.equals(r.getEventType()))
                                .findFirst()
                                .orElse(null),
                        row -> row != null);
    }

    record TestUser(String name, String email) {}
}
