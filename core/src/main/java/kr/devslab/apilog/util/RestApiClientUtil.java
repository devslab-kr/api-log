package kr.devslab.apilog.util;

import kr.devslab.apilog.dto.ApiRequest;
import kr.devslab.apilog.dto.ApiResponse;
import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;

/**
 * HTTP client wrapper that emits {@link ApiCallInitiatedEvent} / {@link ApiCallSuccessEvent} /
 * {@link ApiCallErrorEvent} around every call so they end up in {@code api_log}.
 *
 * <p>Registered as a bean by
 * {@link kr.devslab.apilog.autoconfigure.RestApiClientAutoConfiguration} when
 * {@link RestClient} is on the classpath — no {@code @Component} so consumers
 * don't have to {@code @ComponentScan} the {@code kr.devslab.apilog} package.
 *
 * <p>Two layers of API:
 * <ul>
 *   <li><b>Core:</b> {@link #send(HttpMethod, ApiRequest)}, {@link #sendAsync},
 *       {@link #sendTyped}, {@link #sendAsyncTyped}. Caller supplies the full
 *       {@code ApiRequest} including any {@code requestId} — useful for retries
 *       where multiple attempts must share a correlation key.</li>
 *   <li><b>Convenience wrappers:</b> {@code getSync}, {@code postSync(String, String)},
 *       {@code putSync}, {@code deleteSync}, {@code patchSync}, plus typed/async
 *       variants. Each generates a fresh UUID per call.</li>
 * </ul>
 *
 * <p>All paths funnel through the core methods, so behavior is uniform across HTTP verbs.
 */
public class RestApiClientUtil {

    private final RestClient restClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public RestApiClientUtil(RestClient restClient, ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // Core API — accept HttpMethod + ApiRequest. Use these directly to pass an
    // explicit requestId across retry attempts.
    // =========================================================================

    public ApiResponse send(HttpMethod method, ApiRequest request) {
        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));
        try {
            ResponseEntity<String> entity = exchange(method, request).toEntity(String.class);
            ApiResponse response = ApiResponse.builder()
                    .data(entity.getBody())
                    .statusCode(entity.getStatusCode().value())
                    .build();
            eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));
            return response;
        } catch (RuntimeException e) {
            eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
            throw e;
        }
    }

    public CompletableFuture<ApiResponse> sendAsync(HttpMethod method, ApiRequest request) {
        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<String> entity = exchange(method, request).toEntity(String.class);
                ApiResponse response = ApiResponse.builder()
                        .data(entity.getBody())
                        .statusCode(entity.getStatusCode().value())
                        .build();
                eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));
                return response;
            } catch (RuntimeException e) {
                eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
                throw e;
            }
        });
    }

    public <T> T sendTyped(HttpMethod method, ApiRequest request, Class<T> responseType) {
        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));
        try {
            ResponseEntity<String> entity = exchange(method, request).toEntity(String.class);
            T body = objectMapper.readValue(entity.getBody(), responseType);
            ApiResponse response = ApiResponse.builder()
                    .data(entity.getBody())
                    .statusCode(entity.getStatusCode().value())
                    .build();
            eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));
            return body;
        } catch (Exception e) {
            eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    public <T> CompletableFuture<T> sendAsyncTyped(HttpMethod method, ApiRequest request, Class<T> responseType) {
        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request));
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<String> entity = exchange(method, request).toEntity(String.class);
                T body = objectMapper.readValue(entity.getBody(), responseType);
                ApiResponse response = ApiResponse.builder()
                        .data(entity.getBody())
                        .statusCode(entity.getStatusCode().value())
                        .build();
                eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));
                return body;
            } catch (Exception e) {
                eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Build the RestClient request chain. Body-less HTTP methods (GET, DELETE)
     * are sent without a body even if {@code request.getPayload()} is null;
     * body-carrying methods (POST, PUT, PATCH) use the payload when present.
     *
     * <p>When a payload is present it is always sent as {@code application/json;
     * charset=UTF-8}. The payload arrives here already in JSON canonical form —
     * either serialized by {@link #serialize(Object)} for the typed wrappers,
     * or supplied as a JSON string by the caller of the raw {@code postSync /
     * putSync / patchSync(String endpoint, String payload)} overloads. Without
     * the explicit {@link MediaType#APPLICATION_JSON} hint, Spring's
     * {@code StringHttpMessageConverter} would write the body as
     * {@code text/plain; charset=ISO-8859-1} (its default for String bodies),
     * which any downstream service deserializing with {@code @RequestBody}
     * rejects as Unsupported Media Type.
     */
    private RestClient.ResponseSpec exchange(HttpMethod method, ApiRequest request) {
        RestClient.RequestBodySpec spec = restClient.method(method).uri(request.getEndpoint());
        String payload = request.getPayload();
        if (payload != null) {
            return spec.contentType(MediaType.APPLICATION_JSON).body(payload).retrieve();
        }
        return spec.retrieve();
    }

    // =========================================================================
    // GET convenience wrappers
    // =========================================================================

    public ApiResponse getSync(String endpoint) {
        return send(HttpMethod.GET, ApiRequest.builder().endpoint(endpoint).build());
    }

    public CompletableFuture<ApiResponse> getAsync(String endpoint) {
        return sendAsync(HttpMethod.GET, ApiRequest.builder().endpoint(endpoint).build());
    }

    public <T> T getSyncTyped(String endpoint, Class<T> responseType) {
        return sendTyped(HttpMethod.GET, ApiRequest.builder().endpoint(endpoint).build(), responseType);
    }

    public <T> CompletableFuture<T> getAsyncTyped(String endpoint, Class<T> responseType) {
        return sendAsyncTyped(HttpMethod.GET, ApiRequest.builder().endpoint(endpoint).build(), responseType);
    }

    // =========================================================================
    // POST convenience wrappers
    // =========================================================================

    public ApiResponse postSync(String endpoint, String payload) {
        return send(HttpMethod.POST, ApiRequest.builder().endpoint(endpoint).payload(payload).build());
    }

    public CompletableFuture<ApiResponse> postAsync(String endpoint, String payload) {
        return sendAsync(HttpMethod.POST, ApiRequest.builder().endpoint(endpoint).payload(payload).build());
    }

    public <T> ApiResponse postSync(String endpoint, T requestBody) {
        return postSync(endpoint, serialize(requestBody));
    }

    public <T> CompletableFuture<ApiResponse> postAsync(String endpoint, T requestBody) {
        return postAsync(endpoint, serialize(requestBody));
    }

    public <T> T postSyncTyped(String endpoint, Object requestBody, Class<T> responseType) {
        return sendTyped(HttpMethod.POST,
                ApiRequest.builder().endpoint(endpoint).payload(serialize(requestBody)).build(),
                responseType);
    }

    public <T> CompletableFuture<T> postAsyncTyped(String endpoint, Object requestBody, Class<T> responseType) {
        return sendAsyncTyped(HttpMethod.POST,
                ApiRequest.builder().endpoint(endpoint).payload(serialize(requestBody)).build(),
                responseType);
    }

    // =========================================================================
    // PUT convenience wrappers (v0.5.0+)
    // =========================================================================

    public ApiResponse putSync(String endpoint, String payload) {
        return send(HttpMethod.PUT, ApiRequest.builder().endpoint(endpoint).payload(payload).build());
    }

    public CompletableFuture<ApiResponse> putAsync(String endpoint, String payload) {
        return sendAsync(HttpMethod.PUT, ApiRequest.builder().endpoint(endpoint).payload(payload).build());
    }

    public <T> ApiResponse putSync(String endpoint, T requestBody) {
        return putSync(endpoint, serialize(requestBody));
    }

    public <T> CompletableFuture<ApiResponse> putAsync(String endpoint, T requestBody) {
        return putAsync(endpoint, serialize(requestBody));
    }

    public <T> T putSyncTyped(String endpoint, Object requestBody, Class<T> responseType) {
        return sendTyped(HttpMethod.PUT,
                ApiRequest.builder().endpoint(endpoint).payload(serialize(requestBody)).build(),
                responseType);
    }

    public <T> CompletableFuture<T> putAsyncTyped(String endpoint, Object requestBody, Class<T> responseType) {
        return sendAsyncTyped(HttpMethod.PUT,
                ApiRequest.builder().endpoint(endpoint).payload(serialize(requestBody)).build(),
                responseType);
    }

    // =========================================================================
    // DELETE convenience wrappers (v0.5.0+)
    // =========================================================================

    public ApiResponse deleteSync(String endpoint) {
        return send(HttpMethod.DELETE, ApiRequest.builder().endpoint(endpoint).build());
    }

    public CompletableFuture<ApiResponse> deleteAsync(String endpoint) {
        return sendAsync(HttpMethod.DELETE, ApiRequest.builder().endpoint(endpoint).build());
    }

    public <T> T deleteSyncTyped(String endpoint, Class<T> responseType) {
        return sendTyped(HttpMethod.DELETE, ApiRequest.builder().endpoint(endpoint).build(), responseType);
    }

    public <T> CompletableFuture<T> deleteAsyncTyped(String endpoint, Class<T> responseType) {
        return sendAsyncTyped(HttpMethod.DELETE, ApiRequest.builder().endpoint(endpoint).build(), responseType);
    }

    // =========================================================================
    // PATCH convenience wrappers (v0.5.0+)
    // =========================================================================

    public ApiResponse patchSync(String endpoint, String payload) {
        return send(HttpMethod.PATCH, ApiRequest.builder().endpoint(endpoint).payload(payload).build());
    }

    public CompletableFuture<ApiResponse> patchAsync(String endpoint, String payload) {
        return sendAsync(HttpMethod.PATCH, ApiRequest.builder().endpoint(endpoint).payload(payload).build());
    }

    public <T> ApiResponse patchSync(String endpoint, T requestBody) {
        return patchSync(endpoint, serialize(requestBody));
    }

    public <T> CompletableFuture<ApiResponse> patchAsync(String endpoint, T requestBody) {
        return patchAsync(endpoint, serialize(requestBody));
    }

    public <T> T patchSyncTyped(String endpoint, Object requestBody, Class<T> responseType) {
        return sendTyped(HttpMethod.PATCH,
                ApiRequest.builder().endpoint(endpoint).payload(serialize(requestBody)).build(),
                responseType);
    }

    public <T> CompletableFuture<T> patchAsyncTyped(String endpoint, Object requestBody, Class<T> responseType) {
        return sendAsyncTyped(HttpMethod.PATCH,
                ApiRequest.builder().endpoint(endpoint).payload(serialize(requestBody)).build(),
                responseType);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private String serialize(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }
}
