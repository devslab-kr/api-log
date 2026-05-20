package kr.devslab.apilog.jpa.repository;

import kr.devslab.apilog.jpa.model.ApiLogEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static kr.devslab.apilog.Constants.ERROR;
import static kr.devslab.apilog.Constants.INITIATED;
import static kr.devslab.apilog.Constants.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-database repository test for the JPA backend. JSONB columns are
 * PostgreSQL-specific, so this runs against a Testcontainers Postgres rather
 * than H2.
 *
 * <p>{@code @DataJpaTest} doesn't auto-pick up our autoconfig, so we point
 * {@code @EntityScan} + {@code @EnableJpaRepositories} at the api-log packages
 * via a nested test config.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ApiLogRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Configuration
    @EntityScan(basePackageClasses = ApiLogEntity.class)
    @EnableJpaRepositories(basePackageClasses = ApiLogRepository.class)
    static class RepoConfig {
    }

    @Autowired
    private ApiLogRepository repository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void save_persistsApiLogEntity() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"test\":\"data\"}");
        JsonNode response = objectMapper.readTree("{\"result\":\"success\"}");

        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(SUCCESS)
                .requestId("test-request-id")
                .endpoint("/api/test")
                .payload(payload)
                .response(response)
                .statusCode(200)
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();

        ApiLogEntity saved = repository.save(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventType()).isEqualTo(SUCCESS);
        assertThat(saved.getRequestId()).isEqualTo("test-request-id");
        assertThat(saved.getEndpoint()).isEqualTo("/api/test");
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getResponse()).isEqualTo(response);
        assertThat(saved.getStatusCode()).isEqualTo(200);
    }

    @Test
    void findByRequestId_returnsAllRowsForOneCall() throws Exception {
        String requestId = "test-request-123";
        JsonNode payload = objectMapper.readTree("{\"test\":\"data\"}");

        repository.save(ApiLogEntity.builder()
                .eventType(INITIATED).requestId(requestId).endpoint("/api/test")
                .payload(payload).timestamp(LocalDateTime.now())
                .retryCount(0).isRetry(false).build());

        repository.save(ApiLogEntity.builder()
                .eventType(SUCCESS).requestId(requestId).endpoint("/api/test")
                .payload(payload).response(objectMapper.readTree("{\"result\":\"success\"}"))
                .statusCode(200).timestamp(LocalDateTime.now().plusSeconds(1))
                .retryCount(0).isRetry(false).build());

        List<ApiLogEntity> found = repository.findByRequestId(requestId);

        assertThat(found).hasSize(2);
        assertThat(found).extracting(ApiLogEntity::getEventType)
                .containsExactlyInAnyOrder(INITIATED, SUCCESS);
    }

    @Test
    void findByEventType_returnsAllRowsOfOneEventType() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"test\":\"data\"}");
        JsonNode errMsg = objectMapper.readTree("{\"error\":\"x\"}");

        repository.save(ApiLogEntity.builder()
                .eventType(ERROR).requestId("r-1").endpoint("/a").payload(payload)
                .errorMessage(errMsg).timestamp(LocalDateTime.now())
                .retryCount(0).isRetry(false).build());
        repository.save(ApiLogEntity.builder()
                .eventType(ERROR).requestId("r-2").endpoint("/b").payload(payload)
                .errorMessage(errMsg).timestamp(LocalDateTime.now())
                .retryCount(1).isRetry(false).build());
        repository.save(ApiLogEntity.builder()
                .eventType(SUCCESS).requestId("r-3").endpoint("/c").payload(payload)
                .statusCode(200).timestamp(LocalDateTime.now())
                .retryCount(0).isRetry(false).build());

        List<ApiLogEntity> errors = repository.findByEventType(ERROR);

        assertThat(errors).hasSize(2);
        assertThat(errors).extracting(ApiLogEntity::getRequestId)
                .containsExactlyInAnyOrder("r-1", "r-2");
    }

    @Test
    void save_roundtripsComplexJsonbValues() throws Exception {
        JsonNode complexPayload = objectMapper.readTree("""
                { "user": { "id": 1, "name": "John", "prefs": { "theme": "dark" } },
                  "items": [ { "id": 1 }, { "id": 2 } ] }
                """);
        JsonNode complexError = objectMapper.readTree("""
                { "error": "ValidationError",
                  "details": { "field": "email", "message": "Invalid email format" } }
                """);

        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(ERROR).requestId("complex-request").endpoint("/api/users")
                .payload(complexPayload).errorMessage(complexError)
                .timestamp(LocalDateTime.now())
                .retryCount(1).isRetry(true).build();

        ApiLogEntity saved = repository.save(entity);

        assertThat(saved.getPayload()).isEqualTo(complexPayload);
        assertThat(saved.getErrorMessage()).isEqualTo(complexError);
        assertThat(saved.getPayload().get("user").get("name").asText()).isEqualTo("John");
    }
}
