package com.devs.lab.test.listener;

import com.devs.lab.test.event.ApiCallErrorEvent;
import com.devs.lab.test.event.ApiCallInitiatedEvent;
import com.devs.lab.test.event.ApiCallSuccessEvent;
import com.devs.lab.test.model.dto.ApiRequest;
import com.devs.lab.test.model.dto.ApiResponse;
import com.devs.lab.test.service.ApiLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiEventListenerTest {

    @Mock
    private ApiLogService apiLogService;

    @InjectMocks
    private ApiEventListener apiEventListener;

    @Test
    void handleApiCallInitiated_shouldCallServiceSaveMethod() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("{\"test\":\"data\"}")
                .build();
        ApiCallInitiatedEvent event = new ApiCallInitiatedEvent(this, request);

        // When
        apiEventListener.handleApiCallInitiated(event);

        // Then
        verify(apiLogService).saveApiCallInitiated(event);
    }

    @Test
    void handleApiCallSuccess_shouldCallServiceSaveMethod() {
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
        apiEventListener.handleApiCallSuccess(event);

        // Then
        verify(apiLogService).saveApiCallSuccess(event);
    }

    @Test
    void handleApiCallError_shouldCallServiceSaveMethod() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("{\"test\":\"data\"}")
                .build();
        RuntimeException error = new RuntimeException("Test error");
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request, error, 1, false);

        // When
        apiEventListener.handleApiCallError(event);

        // Then
        verify(apiLogService).saveApiCallError(event);
    }

    @Test
    void handleApiCallInitiated_shouldNotThrowWhenServiceFails() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("{\"test\":\"data\"}")
                .build();
        ApiCallInitiatedEvent event = new ApiCallInitiatedEvent(this, request);

        doThrow(new RuntimeException("Service error")).when(apiLogService).saveApiCallInitiated(event);

        // When & Then - Should not throw exception (비동기 이벤트 실패는 원본에 영향 없음)
        apiEventListener.handleApiCallInitiated(event);

        verify(apiLogService).saveApiCallInitiated(event);
    }

    @Test
    void handleApiCallSuccess_shouldNotThrowWhenServiceFails() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .build();
        ApiResponse response = ApiResponse.builder()
                .data("{\"result\":\"success\"}")
                .statusCode(200)
                .build();
        ApiCallSuccessEvent event = new ApiCallSuccessEvent(this, request, response);

        doThrow(new RuntimeException("Service error")).when(apiLogService).saveApiCallSuccess(event);

        // When & Then - Should not throw exception
        apiEventListener.handleApiCallSuccess(event);

        verify(apiLogService).saveApiCallSuccess(event);
    }

    @Test
    void handleApiCallError_shouldNotThrowWhenServiceFails() {
        // Given
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .build();
        RuntimeException error = new RuntimeException("API error");
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request, error, 0, false);

        doThrow(new RuntimeException("Service error")).when(apiLogService).saveApiCallError(event);

        // When & Then - Should not throw exception
        apiEventListener.handleApiCallError(event);

        verify(apiLogService).saveApiCallError(event);
    }
}