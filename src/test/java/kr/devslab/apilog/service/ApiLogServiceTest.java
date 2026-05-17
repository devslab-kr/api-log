package kr.devslab.apilog.service;

import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.model.ApiLogEntity;
import kr.devslab.apilog.model.dto.ApiRequest;
import kr.devslab.apilog.model.dto.ApiResponse;
import kr.devslab.apilog.repository.ApiLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static kr.devslab.apilog.Constants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiLogServiceTest {

    @Mock
    private ApiLogRepository repository;

    private ObjectMapper objectMapper;
    private ApiLogService apiLogService;
    private ArgumentCaptor<ApiLogEntity> entityCaptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper(); // 실제 ObjectMapper 사용
        apiLogService = new ApiLogService(repository, objectMapper);
        entityCaptor = ArgumentCaptor.forClass(ApiLogEntity.class);
    }

    @Test
    void saveApiCallInitiated_shouldSaveEntityWithCorrectData() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("{\"test\":\"data\"}")
                .build();
        ApiCallInitiatedEvent event = new ApiCallInitiatedEvent(this, request);

        // When
        apiLogService.saveApiCallInitiated(event);

        // Then
        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();

        assertThat(saved.getEventType()).isEqualTo(INITIATED);
        assertThat(saved.getRequestId()).isEqualTo(request.getRequestId());
        assertThat(saved.getEndpoint()).isEqualTo("/api/test");
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getIsRetry()).isFalse();
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void saveApiCallSuccess_shouldSaveEntityWithResponseData() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("{\"test\":\"data\"}")
                .build();
        ApiResponse response = ApiResponse.builder()
                .data("{\"result\":\"success\"}")
                .statusCode(200)
                .build();
        ApiCallSuccessEvent event = new ApiCallSuccessEvent(this, request, response);

        // When
        apiLogService.saveApiCallSuccess(event);

        // Then
        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();

        assertThat(saved.getEventType()).isEqualTo(SUCCESS);
        assertThat(saved.getRequestId()).isEqualTo(request.getRequestId());
        assertThat(saved.getEndpoint()).isEqualTo("/api/test");
        assertThat(saved.getStatusCode()).isEqualTo(200);
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.getIsRetry()).isFalse();
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void saveApiCallError_shouldSaveEntityWithErrorData() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("{\"test\":\"data\"}")
                .build();
        RuntimeException error = new RuntimeException("Test error");
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request, error, 1, false);

        // When
        apiLogService.saveApiCallError(event);

        // Then
        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();

        assertThat(saved.getEventType()).isEqualTo(ERROR);
        assertThat(saved.getRequestId()).isEqualTo(request.getRequestId());
        assertThat(saved.getEndpoint()).isEqualTo("/api/test");
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getIsRetry()).isFalse();
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void saveApiCallError_shouldSaveRetryError() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("{\"test\":\"data\"}")
                .build();
        RuntimeException error = new RuntimeException("Retry error");
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request, error, 2, true);

        // When
        apiLogService.saveApiCallError(event);

        // Then
        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();

        assertThat(saved.getEventType()).isEqualTo(RETRY_ERROR);
        assertThat(saved.getRetryCount()).isEqualTo(2);
        assertThat(saved.getIsRetry()).isTrue();
    }

    @Test
    void saveApiCallInitiated_shouldHandleNullPayload() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .build(); // payload is null
        ApiCallInitiatedEvent event = new ApiCallInitiatedEvent(this, request);

        // When
        apiLogService.saveApiCallInitiated(event);

        // Then
        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();

        assertThat(saved.getEventType()).isEqualTo(INITIATED);
        assertThat(saved.getEndpoint()).isEqualTo("/api/test");
        assertThat(saved.getPayload()).isNotNull(); // Should create empty ObjectNode
    }

    @Test
    void saveApiCallError_writesStructuredErrorMessage() {
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .build();
        IllegalStateException error = new IllegalStateException("connection broken");
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request, error, 0, false);

        apiLogService.saveApiCallError(event);

        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();

        // Should be a structured {type, message} object, not just a raw string.
        assertThat(saved.getErrorMessage()).isNotNull();
        assertThat(saved.getErrorMessage().get("type").asText())
                .isEqualTo("java.lang.IllegalStateException");
        assertThat(saved.getErrorMessage().get("message").asText())
                .isEqualTo("connection broken");
        // No upstream response body for a non-HTTP exception.
        assertThat(saved.getErrorMessage().has("responseBody")).isFalse();
        // Non-HTTP exceptions don't carry a status code.
        assertThat(saved.getStatusCode()).isNull();
    }

    @Test
    void saveApiCallError_extractsHttpStatusAndResponseBody() {
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .build();
        HttpClientErrorException error = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                org.springframework.http.HttpHeaders.EMPTY,
                "{\"error\":\"user not found\"}".getBytes(),
                null
        );
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request, error, 0, false);

        apiLogService.saveApiCallError(event);

        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();

        // status_code lifted off the Spring exception — was always NULL before v0.4.0.
        assertThat(saved.getStatusCode()).isEqualTo(404);
        // error_message carries type + message + upstream responseBody.
        assertThat(saved.getErrorMessage().get("type").asText())
                .contains("HttpClientErrorException");
        assertThat(saved.getErrorMessage().get("responseBody").asText())
                .isEqualTo("{\"error\":\"user not found\"}");
    }

    @Test
    void saveApiCallInitiated_shouldHandleInvalidJson() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("invalid json {")
                .build();
        ApiCallInitiatedEvent event = new ApiCallInitiatedEvent(this, request);

        // When
        apiLogService.saveApiCallInitiated(event);

        // Then
        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();

        assertThat(saved.getEventType()).isEqualTo(INITIATED);
        assertThat(saved.getEndpoint()).isEqualTo("/api/test");
        assertThat(saved.getPayload()).isNotNull(); // Should create fallback node with raw field
        assertThat(saved.getPayload().has("raw")).isTrue(); // Should have raw field for invalid JSON
    }
}