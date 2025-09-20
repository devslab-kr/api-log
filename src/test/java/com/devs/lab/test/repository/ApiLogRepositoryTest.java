package com.devs.lab.test.repository;

import com.devs.lab.test.model.ApiLogEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static com.devs.lab.test.Constants.*;
import static org.assertj.core.api.Assertions.assertThat;

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

    @Autowired
    private ApiLogRepository repository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void save_shouldPersistApiLogEntity() throws Exception {
        // Given
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

        // When
        ApiLogEntity saved = repository.save(entity);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEventType()).isEqualTo(SUCCESS);
        assertThat(saved.getRequestId()).isEqualTo("test-request-id");
        assertThat(saved.getEndpoint()).isEqualTo("/api/test");
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getResponse()).isEqualTo(response);
        assertThat(saved.getStatusCode()).isEqualTo(200);
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getIsRetry()).isFalse();
    }

    @Test
    void findByRequestId_shouldReturnEntitiesWithSameRequestId() throws Exception {
        // Given
        String requestId = "test-request-123";
        JsonNode payload = objectMapper.readTree("{\"test\":\"data\"}");

        ApiLogEntity initiated = ApiLogEntity.builder()
                .eventType(INITIATED)
                .requestId(requestId)
                .endpoint("/api/test")
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();

        ApiLogEntity success = ApiLogEntity.builder()
                .eventType(SUCCESS)
                .requestId(requestId)
                .endpoint("/api/test")
                .payload(payload)
                .response(objectMapper.readTree("{\"result\":\"success\"}"))
                .statusCode(200)
                .timestamp(LocalDateTime.now().plusSeconds(1))
                .retryCount(0)
                .isRetry(false)
                .build();

        repository.save(initiated);
        repository.save(success);

        // When
        List<ApiLogEntity> found = repository.findByRequestId(requestId);

        // Then
        assertThat(found).hasSize(2);
        assertThat(found).extracting(ApiLogEntity::getEventType)
                .containsExactlyInAnyOrder(INITIATED, SUCCESS);
        assertThat(found).allMatch(entity -> entity.getRequestId().equals(requestId));
    }

    @Test
    void findByEventType_shouldReturnEntitiesWithSameEventType() throws Exception {
        // Given
        JsonNode payload = objectMapper.readTree("{\"test\":\"data\"}");

        ApiLogEntity error1 = ApiLogEntity.builder()
                .eventType(ERROR)
                .requestId("request-1")
                .endpoint("/api/test1")
                .payload(payload)
                .errorMessage(objectMapper.readTree("{\"error\":\"error1\"}"))
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();

        ApiLogEntity error2 = ApiLogEntity.builder()
                .eventType(ERROR)
                .requestId("request-2")
                .endpoint("/api/test2")
                .payload(payload)
                .errorMessage(objectMapper.readTree("{\"error\":\"error2\"}"))
                .timestamp(LocalDateTime.now())
                .retryCount(1)
                .isRetry(false)
                .build();

        ApiLogEntity success = ApiLogEntity.builder()
                .eventType(SUCCESS)
                .requestId("request-3")
                .endpoint("/api/test3")
                .payload(payload)
                .response(objectMapper.readTree("{\"result\":\"success\"}"))
                .statusCode(200)
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();

        repository.save(error1);
        repository.save(error2);
        repository.save(success);

        // When
        List<ApiLogEntity> errors = repository.findByEventType(ERROR);

        // Then
        assertThat(errors).hasSize(2);
        assertThat(errors).allMatch(entity -> entity.getEventType().equals(ERROR));
        assertThat(errors).extracting(ApiLogEntity::getRequestId)
                .containsExactlyInAnyOrder("request-1", "request-2");
    }

    @Test
    void findByEndpoint_shouldReturnEntitiesWithSameEndpoint() throws Exception {
        // Given
        String endpoint = "/api/users";
        JsonNode payload = objectMapper.readTree("{\"name\":\"John\"}");

        ApiLogEntity entity1 = ApiLogEntity.builder()
                .eventType(INITIATED)
                .requestId("request-1")
                .endpoint(endpoint)
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();

        ApiLogEntity entity2 = ApiLogEntity.builder()
                .eventType(SUCCESS)
                .requestId("request-1")
                .endpoint(endpoint)
                .payload(payload)
                .response(objectMapper.readTree("{\"id\":1,\"name\":\"John\"}"))
                .statusCode(201)
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();

        ApiLogEntity otherEndpoint = ApiLogEntity.builder()
                .eventType(INITIATED)
                .requestId("request-2")
                .endpoint("/api/products")
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();

        repository.save(entity1);
        repository.save(entity2);
        repository.save(otherEndpoint);

        // When
        List<ApiLogEntity> found = repository.findByEndpoint(endpoint);

        // Then
        assertThat(found).hasSize(2);
        assertThat(found).allMatch(entity -> entity.getEndpoint().equals(endpoint));
        assertThat(found).extracting(ApiLogEntity::getEventType)
                .containsExactlyInAnyOrder(INITIATED, SUCCESS);
    }

    @Test
    void save_shouldHandleJsonbFields() throws Exception {
        // Given - JSONB 필드들이 제대로 저장되는지 테스트
        JsonNode complexPayload = objectMapper.readTree("""
            {
                "user": {
                    "id": 1,
                    "name": "John Doe",
                    "preferences": {
                        "theme": "dark",
                        "notifications": true
                    }
                },
                "items": [
                    {"id": 1, "name": "Item 1"},
                    {"id": 2, "name": "Item 2"}
                ]
            }
            """);

        JsonNode complexError = objectMapper.readTree("""
            {
                "error": "ValidationError",
                "details": {
                    "field": "email",
                    "message": "Invalid email format"
                },
                "timestamp": "2023-12-01T10:00:00Z"
            }
            """);

        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(ERROR)
                .requestId("complex-request")
                .endpoint("/api/users")
                .payload(complexPayload)
                .errorMessage(complexError)
                .timestamp(LocalDateTime.now())
                .retryCount(1)
                .isRetry(true)
                .build();

        // When
        ApiLogEntity saved = repository.save(entity);

        // Then
        assertThat(saved.getPayload()).isEqualTo(complexPayload);
        assertThat(saved.getErrorMessage()).isEqualTo(complexError);
        assertThat(saved.getPayload().get("user").get("name").asText()).isEqualTo("John Doe");
        assertThat(saved.getErrorMessage().get("details").get("field").asText()).isEqualTo("email");
    }
}