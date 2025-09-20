package com.devs.lab.test.util;

import com.devs.lab.test.event.ApiCallErrorEvent;
import com.devs.lab.test.event.ApiCallInitiatedEvent;
import com.devs.lab.test.event.ApiCallSuccessEvent;
import com.devs.lab.test.model.dto.ApiRequest;
import com.devs.lab.test.model.dto.ApiResponse;
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
                String responseData = restClient.post()
                        .uri(endpoint)
                        .body(payload)
                        .retrieve()
                        .body(String.class);
                ApiResponse response = ApiResponse.builder()
                        .data(responseData)
                        .statusCode(200)
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
            String responseData = restClient.post()
                    .uri(endpoint)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            ApiResponse response = ApiResponse.builder()
                    .data(responseData)
                    .statusCode(200)
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
                String responseData = restClient.get()
                        .uri(endpoint)
                        .retrieve()
                        .body(String.class);
                ApiResponse response = ApiResponse.builder()
                        .data(responseData)
                        .statusCode(200)
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
            String responseData = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .body(String.class);
            ApiResponse response = ApiResponse.builder()
                    .data(responseData)
                    .statusCode(200)
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