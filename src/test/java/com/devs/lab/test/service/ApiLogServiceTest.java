package com.devs.lab.test.service;

import com.devs.lab.test.event.ApiCallErrorEvent;
import com.devs.lab.test.event.ApiCallInitiatedEvent;
import com.devs.lab.test.event.ApiCallSuccessEvent;
import com.devs.lab.test.model.ApiLogEntity;
import com.devs.lab.test.model.dto.ApiRequest;
import com.devs.lab.test.model.dto.ApiResponse;
import com.devs.lab.test.repository.ApiLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.devs.lab.test.Constants.*;
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