package kr.devslab.apilog.r2dbc.writer;

import kr.devslab.apilog.dto.ApiRequest;
import kr.devslab.apilog.dto.ApiResponse;
import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.spi.ApiLogWriter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the R2DBC backend — boots a Spring context with the
 * reactive autoconfig, drives events through the registered
 * {@link ApiLogWriter}, then asserts the rows landed in PostgreSQL using a
 * second JDBC-free {@link DatabaseClient} query.
 *
 * <p>This is also the regression guard for the v0.6.0 "R2DBC actually works"
 * promise — if the writer's parameter binding or the reactive schema
 * initializer breaks, this test fails before any release reaches Maven Central.
 */
@SpringBootTest
@Testcontainers
class R2dbcApiLogWriterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("apilog_r2dbc_it")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // Spring Boot's R2dbcAutoConfiguration reads spring.r2dbc.* — the
        // r2dbc:postgresql:// scheme is what tells it which driver to load.
        registry.add("spring.r2dbc.url", () -> String.format(
                "r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getMappedPort(5432),
                postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    ApiLogWriter writer;

    @Autowired
    DatabaseClient databaseClient;

    @BeforeEach
    void clearTable() {
        databaseClient.sql("DELETE FROM api_log").fetch().rowsUpdated().block();
    }

    @Test
    void writer_isWiredFromR2dbcBackend() {
        // The R2DBC writer has no @Transactional today so it's not proxied,
        // but matching the JPA/MyBatis tests' substring approach keeps it
        // proxy-safe if that ever changes.
        assertThat(writer.getClass().getName()).contains("R2dbcApiLogWriter");
    }

    @Test
    void writeInitiated_insertsRowWithJsonbPayload() {
        String reqId = UUID.randomUUID().toString();
        ApiRequest request = ApiRequest.builder()
                .endpoint("/charges")
                .payload("{\"amount\":100}")
                .requestId(reqId)
                .build();

        writer.writeInitiated(new ApiCallInitiatedEvent(this, request));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Map<String, Object>> rows = fetchByRequestId(reqId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("event_type")).isEqualTo("INITIATED");
            assertThat(row.get("endpoint")).isEqualTo("/charges");
            assertThat(row.get("retry_count")).isEqualTo(0);
            assertThat(row.get("is_retry")).isEqualTo(false);
            // payload is JSONB; toString round-trips to canonical JSON
            assertThat(row.get("payload").toString()).contains("\"amount\": 100");
        });
    }

    @Test
    void writeSuccess_insertsRowWithResponseJsonAndStatusCode() {
        String reqId = UUID.randomUUID().toString();
        ApiRequest request = ApiRequest.builder()
                .endpoint("/charges").requestId(reqId).build();
        ApiResponse response = ApiResponse.builder()
                .data("{\"id\":\"ch_1\"}").statusCode(201).build();

        writer.writeSuccess(new ApiCallSuccessEvent(this, request, response));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Map<String, Object>> rows = fetchByRequestId(reqId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("event_type")).isEqualTo("SUCCESS");
            assertThat(row.get("status_code")).isEqualTo(201);
            assertThat(row.get("response").toString()).contains("\"id\": \"ch_1\"");
        });
    }

    @Test
    void writeError_insertsStructuredErrorMessage() {
        String reqId = UUID.randomUUID().toString();
        ApiRequest request = ApiRequest.builder()
                .endpoint("/charges").requestId(reqId).build();
        IllegalStateException error = new IllegalStateException("connection broken");
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request, error, 0, false);

        writer.writeError(event);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Map<String, Object>> rows = fetchByRequestId(reqId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("event_type")).isEqualTo("ERROR");
            // Non-HTTP exception → no status_code
            assertThat(row.get("status_code")).isNull();
            assertThat(row.get("error_message").toString())
                    .contains("\"type\": \"java.lang.IllegalStateException\"")
                    .contains("\"message\": \"connection broken\"");
        });
    }

    @Test
    void writeError_marksRetryErrorWhenRetryFlagSet() {
        String reqId = UUID.randomUUID().toString();
        ApiRequest request = ApiRequest.builder()
                .endpoint("/charges").requestId(reqId).build();
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request,
                new RuntimeException("retry"), 2, true);

        writer.writeError(event);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Map<String, Object>> rows = fetchByRequestId(reqId);
            assertThat(rows).hasSize(1);
            Map<String, Object> row = rows.get(0);
            assertThat(row.get("event_type")).isEqualTo("RETRY_ERROR");
            assertThat(row.get("retry_count")).isEqualTo(2);
            assertThat(row.get("is_retry")).isEqualTo(true);
        });
    }

    private List<Map<String, Object>> fetchByRequestId(String requestId) {
        return databaseClient.sql("""
                        SELECT event_type, endpoint, payload::text AS payload,
                               response::text AS response, status_code,
                               error_message::text AS error_message,
                               retry_count, is_retry
                        FROM api_log
                        WHERE request_id = :rid
                        ORDER BY id ASC
                        """)
                .bind("rid", requestId)
                .fetch()
                .all()
                .collectList()
                .block();
    }
}
