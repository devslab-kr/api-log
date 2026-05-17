package kr.devslab.apilog.util;

import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.model.dto.ApiRequest;
import kr.devslab.apilog.model.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

@Component
public class RestApiClientUtil {
    private final RestClient restClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public RestApiClientUtil(RestClient restClient, ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ApiResponse> postAsync(String endpoint, String payload) {
        ApiRequest request = ApiRequest.builder()
                .endpoint(endpoint)
                .payload(payload)
                .build();
        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));

        return CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<String> entity = restClient.post()
                        .uri(endpoint)
                        .body(payload)
                        .retrieve()
                        .toEntity(String.class);
                ApiResponse response = ApiResponse.builder()
                        .data(entity.getBody())
                        .statusCode(entity.getStatusCode().value())
                        .build();
                eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));
                return response;
            } catch (Exception e) {
                eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
                throw e;
            }
        });
    }

    public ApiResponse postSync(String endpoint, String payload) {
        ApiRequest request = ApiRequest.builder()
                .endpoint(endpoint)
                .payload(payload)
                .build();
        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));

        try {
            ResponseEntity<String> entity = restClient.post()
                    .uri(endpoint)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);
            ApiResponse response = ApiResponse.builder()
                    .data(entity.getBody())
                    .statusCode(entity.getStatusCode().value())
                    .build();
            eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));
            return response;
        } catch (Exception e) {
            eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
            throw e;
        }
    }

    public CompletableFuture<ApiResponse> getAsync(String endpoint) {
        ApiRequest request = ApiRequest.builder()
                .endpoint(endpoint)
                .build();
        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));

        return CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<String> entity = restClient.get()
                        .uri(endpoint)
                        .retrieve()
                        .toEntity(String.class);
                ApiResponse response = ApiResponse.builder()
                        .data(entity.getBody())
                        .statusCode(entity.getStatusCode().value())
                        .build();
                eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));
                return response;
            } catch (Exception e) {
                eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
                throw e;
            }
        });
    }

    public ApiResponse getSync(String endpoint) {
        ApiRequest request = ApiRequest.builder()
                .endpoint(endpoint)
                .build();
        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));

        try {
            ResponseEntity<String> entity = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .toEntity(String.class);
            ApiResponse response = ApiResponse.builder()
                    .data(entity.getBody())
                    .statusCode(entity.getStatusCode().value())
                    .build();
            eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));
            return response;
        } catch (Exception e) {
            eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
            throw e;
        }
    }

    // ====== 제네릭 메서드들 (DTO 객체 지원) ======

    public <T> CompletableFuture<ApiResponse> postAsync(String endpoint, T requestBody) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(requestBody);
            return postAsync(endpoint, jsonPayload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }

    public <T> ApiResponse postSync(String endpoint, T requestBody) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(requestBody);
            return postSync(endpoint, jsonPayload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }

    public <T> CompletableFuture<T> postAsyncTyped(String endpoint, Object requestBody, Class<T> responseType) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(requestBody);
            ApiRequest request = ApiRequest.builder()
                    .endpoint(endpoint)
                    .payload(jsonPayload)
                    .build();
            eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));

            return CompletableFuture.supplyAsync(() -> {
                try {
                    ResponseEntity<String> responseEntity = restClient.post()
                            .uri(endpoint)
                            .body(jsonPayload)
                            .retrieve()
                            .toEntity(String.class);

                    T responseBody = objectMapper.readValue(responseEntity.getBody(), responseType);

                    ApiResponse response = ApiResponse.builder()
                            .data(responseEntity.getBody())
                            .statusCode(responseEntity.getStatusCode().value())
                            .build();
                    eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));

                    return responseBody;
                } catch (Exception e) {
                    eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
                    throw new RuntimeException(e);
                }
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }

    public <T> T postSyncTyped(String endpoint, Object requestBody, Class<T> responseType) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(requestBody);
            ApiRequest request = ApiRequest.builder()
                    .endpoint(endpoint)
                    .payload(jsonPayload)
                    .build();
            eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));

            ResponseEntity<String> responseEntity = restClient.post()
                    .uri(endpoint)
                    .body(jsonPayload)
                    .retrieve()
                    .toEntity(String.class);

            T responseBody = objectMapper.readValue(responseEntity.getBody(), responseType);

            ApiResponse response = ApiResponse.builder()
                    .data(responseEntity.getBody())
                    .statusCode(responseEntity.getStatusCode().value())
                    .build();
            eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));

            return responseBody;
        } catch (Exception e) {
            ApiRequest request = ApiRequest.builder()
                    .endpoint(endpoint)
                    .build();
            eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
            throw new RuntimeException(e);
        }
    }

    public <T> CompletableFuture<T> getAsyncTyped(String endpoint, Class<T> responseType) {
        ApiRequest request = ApiRequest.builder()
                .endpoint(endpoint)
                .build();
        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));

        return CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<String> responseEntity = restClient.get()
                        .uri(endpoint)
                        .retrieve()
                        .toEntity(String.class);

                T responseBody = objectMapper.readValue(responseEntity.getBody(), responseType);

                ApiResponse response = ApiResponse.builder()
                        .data(responseEntity.getBody())
                        .statusCode(responseEntity.getStatusCode().value())
                        .build();
                eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));

                return responseBody;
            } catch (Exception e) {
                eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
                throw new RuntimeException(e);
            }
        });
    }

    public <T> T getSyncTyped(String endpoint, Class<T> responseType) {
        ApiRequest request = ApiRequest.builder()
                .endpoint(endpoint)
                .build();
        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));

        try {
            ResponseEntity<String> responseEntity = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .toEntity(String.class);

            T responseBody = objectMapper.readValue(responseEntity.getBody(), responseType);

            ApiResponse response = ApiResponse.builder()
                    .data(responseEntity.getBody())
                    .statusCode(responseEntity.getStatusCode().value())
                    .build();
            eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));

            return responseBody;
        } catch (Exception e) {
            eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
            throw new RuntimeException(e);
        }
    }
}