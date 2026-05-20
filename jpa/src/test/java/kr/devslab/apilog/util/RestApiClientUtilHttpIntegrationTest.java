package kr.devslab.apilog.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.devslab.apilog.dto.ApiRequest;
import kr.devslab.apilog.dto.ApiResponse;
import kr.devslab.apilog.jpa.model.ApiLogEntity;
import kr.devslab.apilog.jpa.repository.ApiLogRepository;
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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end HTTP integration test for {@link RestApiClientUtil} on the JPA
 * backend.
 *
 * <p>What this proves that the unit tests do NOT:
 * <ul>
 *   <li>The real HTTP status code reaches both {@link ApiResponse#getStatusCode()}
 *       and the {@code status_code} column in {@code api_log} (regression guard
 *       for the v0.4.0 "always 200" bug).</li>
 *   <li>4xx/5xx responses produce ERROR rows with status code AND a structured
 *       {@code error_message} JSON containing {@code type}, {@code message},
 *       and (when the upstream had a body) {@code responseBody}.</li>
 *   <li>Async paths through {@code CompletableFuture} still publish events.</li>
 *   <li>A caller-supplied {@code requestId} via {@code send()} correlates all
 *       retry attempts in {@code api_log}.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
class RestApiClientUtilHttpIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("apilog_it")
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
    static class HttpTestConfig {
        @Bean
        @Primary
        RestClient testRestClient() {
            return RestClient.builder()
                    .baseUrl(mockServer.url("/").toString())
                    .build();
        }
    }

    @Autowired
    RestApiClientUtil api;

    @Autowired
    ApiLogRepository repository;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clearLog() throws InterruptedException {
        repository.deleteAll();
        while (mockServer.getRequestCount() > 0 && mockServer.takeRequest(1, TimeUnit.MILLISECONDS) != null) {
            // discard
        }
    }

    // ------------------------------------------------------------------ //
    // Status code propagation                                              //
    // ------------------------------------------------------------------ //

    @Test
    void getSync_2xx_propagatesActualStatusCodeIntoApiLog() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":1,\"name\":\"Ada\"}"));

        ApiResponse resp = api.getSync("/users/1");

        assertThat(resp.getStatusCode()).isEqualTo(201);

        ApiLogEntity successRow = waitForRow("SUCCESS");
        assertThat(successRow.getStatusCode()).isEqualTo(201);
        assertThat(successRow.getResponse().get("id").asInt()).isEqualTo(1);
        assertThat(successRow.getResponse().get("name").asText()).isEqualTo("Ada");
    }

    @Test
    void postSync_writesInitiatedAndSuccessRowsWithSameRequestId() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true}"));

        api.postSync("/widgets", "{\"sku\":\"X-1\"}");

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<ApiLogEntity> rows = repository.findAll();
            assertThat(rows).hasSize(2);
            assertThat(rows.stream().map(ApiLogEntity::getRequestId).distinct()).hasSize(1);
            assertThat(rows.stream().map(ApiLogEntity::getEventType))
                    .containsExactlyInAnyOrder("INITIATED", "SUCCESS");
        });
    }

    @Test
    void clientError_4xx_capturesStatusCodeAndStructuredErrorMessage() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"user not found\",\"code\":\"USR_404\"}"));

        assertThatThrownBy(() -> api.getSync("/users/999"))
                .isInstanceOf(RestClientException.class);

        ApiLogEntity errorRow = waitForRow("ERROR");

        assertThat(errorRow.getStatusCode()).isEqualTo(404);
        JsonNode err = errorRow.getErrorMessage();
        assertThat(err.get("type").asText()).contains("HttpClientErrorException");
        assertThat(err.has("message")).isTrue();
        assertThat(err.get("responseBody").asText())
                .isEqualTo("{\"error\":\"user not found\",\"code\":\"USR_404\"}");
    }

    @Test
    void serverError_5xx_capturesStatusCode() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setBody("service unavailable"));

        assertThatThrownBy(() -> api.postSync("/users", "{}"))
                .isInstanceOf(RestClientException.class);

        ApiLogEntity errorRow = waitForRow("ERROR");
        assertThat(errorRow.getStatusCode()).isEqualTo(503);
        assertThat(errorRow.getErrorMessage().get("responseBody").asText())
                .isEqualTo("service unavailable");
    }

    @Test
    void getAsync_publishesEventsAndLogsRows() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true}"));

        CompletableFuture<ApiResponse> future = api.getAsync("/health");
        ApiResponse resp = future.get(5, TimeUnit.SECONDS);

        assertThat(resp.getStatusCode()).isEqualTo(200);
        assertThat(resp.getData()).isEqualTo("{\"ok\":true}");

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(repository.findAll()).extracting(ApiLogEntity::getEventType)
                        .containsExactlyInAnyOrder("INITIATED", "SUCCESS"));
    }

    @Test
    void send_withCustomRequestId_correlatesAttemptsForRetryTimeline() {
        String correlationId = UUID.randomUUID().toString();

        mockServer.enqueue(new MockResponse().setResponseCode(503));
        mockServer.enqueue(new MockResponse().setResponseCode(503));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true}"));

        ApiRequest req = ApiRequest.builder()
                .endpoint("/charges")
                .payload("{\"amount\":100}")
                .requestId(correlationId)
                .build();

        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                api.send(HttpMethod.POST, req);
                last = null;
                break;
            } catch (Exception e) {
                last = e;
            }
        }
        assertThat(last).isNull();

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<ApiLogEntity> rows = repository.findAll().stream()
                    .filter(r -> correlationId.equals(r.getRequestId()))
                    .toList();
            assertThat(rows).hasSize(6);
            assertThat(rows.stream().map(ApiLogEntity::getEventType))
                    .filteredOn(t -> t.equals("INITIATED")).hasSize(3);
            assertThat(rows.stream().map(ApiLogEntity::getEventType))
                    .filteredOn(t -> t.equals("ERROR")).hasSize(2);
            assertThat(rows.stream().map(ApiLogEntity::getEventType))
                    .filteredOn(t -> t.equals("SUCCESS")).hasSize(1);
        });
    }

    @Test
    void putSync_routesPutAndLogsCorrectly() throws InterruptedException {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        api.putSync("/users/1", "{\"name\":\"Ada-renamed\"}");
        assertThat(mockServer.takeRequest().getMethod()).isEqualTo("PUT");
        waitForRow("SUCCESS");
    }

    @Test
    void deleteSync_routesDeleteAndLogsCorrectly() throws InterruptedException {
        mockServer.enqueue(new MockResponse().setResponseCode(204));
        api.deleteSync("/users/1");
        assertThat(mockServer.takeRequest().getMethod()).isEqualTo("DELETE");
        ApiLogEntity success = waitForRow("SUCCESS");
        assertThat(success.getStatusCode()).isEqualTo(204);
    }

    @Test
    void patchSync_routesPatchAndLogsCorrectly() throws InterruptedException {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        api.patchSync("/users/1", "{\"email\":\"new@example.com\"}");
        assertThat(mockServer.takeRequest().getMethod()).isEqualTo("PATCH");
        waitForRow("SUCCESS");
    }

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
}
