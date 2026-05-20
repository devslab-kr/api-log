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
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reactive ({@link WebClient}-backed) counterpart to {@link RestApiClientUtil}.
 *
 * <p>Mirrors the blocking client's API surface — all five HTTP verbs × raw / typed
 * responses, plus the {@code send()} / {@code sendTyped()} cores — but returns
 * {@link Mono} so callers compose with the rest of their reactive pipeline.
 *
 * <p>Same event-publishing contract as {@link RestApiClientUtil}: every call
 * fires an {@link ApiCallInitiatedEvent} before the request, then a
 * {@link ApiCallSuccessEvent} on a successful response, or an
 * {@link ApiCallErrorEvent} on failure. The listener then writes the
 * {@code api_log} rows asynchronously — no reactor-vs-Spring-AOP friction
 * because the event publish is fire-and-forget.
 *
 * <p>Registered automatically by
 * {@link kr.devslab.apilog.autoconfigure.ReactiveApiClientAutoConfiguration}
 * when {@code org.springframework:spring-webflux} is on the classpath (the
 * starter declares it as optional).
 */
public class ReactiveApiClientUtil {

    private final WebClient webClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ReactiveApiClientUtil(WebClient webClient,
                                  ApplicationEventPublisher eventPublisher,
                                  ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    // ====== Core ============================================================

    /**
     * Most general entry point. Use this when the caller needs control over the
     * {@link ApiRequest} (e.g., providing a stable {@code requestId} for retry
     * correlation across attempts).
     */
    public Mono<ApiResponse> send(HttpMethod method, ApiRequest request) {
        return Mono.fromRunnable(() ->
                        eventPublisher.publishEvent(new ApiCallInitiatedEvent(this, request)))
                .then(exchange(method, request))
                .map(entity -> {
                    ApiResponse response = ApiResponse.builder()
                            .data(entity.getBody())
                            .statusCode(entity.getStatusCode().value())
                            .build();
                    eventPublisher.publishEvent(new ApiCallSuccessEvent(this, request, response));
                    return response;
                })
                .doOnError(error ->
                        eventPublisher.publishEvent(new ApiCallErrorEvent(this, request, error, 0, false)));
    }

    public <T> Mono<T> sendTyped(HttpMethod method, ApiRequest request, Class<T> responseType) {
        return send(method, request).map(resp -> deserialize(resp.getData(), responseType));
    }

    // ====== GET =============================================================

    public Mono<ApiResponse> get(String endpoint) {
        return send(HttpMethod.GET, ApiRequest.builder().endpoint(endpoint).build());
    }

    public <T> Mono<T> getTyped(String endpoint, Class<T> responseType) {
        return sendTyped(HttpMethod.GET,
                ApiRequest.builder().endpoint(endpoint).build(),
                responseType);
    }

    // ====== POST ============================================================

    public Mono<ApiResponse> post(String endpoint, String payload) {
        return send(HttpMethod.POST,
                ApiRequest.builder().endpoint(endpoint).payload(payload).build());
    }

    public <T> Mono<ApiResponse> post(String endpoint, T requestBody) {
        return post(endpoint, serialize(requestBody));
    }

    public <T> Mono<T> postTyped(String endpoint, Object requestBody, Class<T> responseType) {
        return sendTyped(HttpMethod.POST,
                ApiRequest.builder().endpoint(endpoint).payload(serialize(requestBody)).build(),
                responseType);
    }

    // ====== PUT =============================================================

    public Mono<ApiResponse> put(String endpoint, String payload) {
        return send(HttpMethod.PUT,
                ApiRequest.builder().endpoint(endpoint).payload(payload).build());
    }

    public <T> Mono<ApiResponse> put(String endpoint, T requestBody) {
        return put(endpoint, serialize(requestBody));
    }

    public <T> Mono<T> putTyped(String endpoint, Object requestBody, Class<T> responseType) {
        return sendTyped(HttpMethod.PUT,
                ApiRequest.builder().endpoint(endpoint).payload(serialize(requestBody)).build(),
                responseType);
    }

    // ====== DELETE ==========================================================

    public Mono<ApiResponse> delete(String endpoint) {
        return send(HttpMethod.DELETE, ApiRequest.builder().endpoint(endpoint).build());
    }

    public <T> Mono<T> deleteTyped(String endpoint, Class<T> responseType) {
        return sendTyped(HttpMethod.DELETE,
                ApiRequest.builder().endpoint(endpoint).build(),
                responseType);
    }

    // ====== PATCH ===========================================================

    public Mono<ApiResponse> patch(String endpoint, String payload) {
        return send(HttpMethod.PATCH,
                ApiRequest.builder().endpoint(endpoint).payload(payload).build());
    }

    public <T> Mono<ApiResponse> patch(String endpoint, T requestBody) {
        return patch(endpoint, serialize(requestBody));
    }

    public <T> Mono<T> patchTyped(String endpoint, Object requestBody, Class<T> responseType) {
        return sendTyped(HttpMethod.PATCH,
                ApiRequest.builder().endpoint(endpoint).payload(serialize(requestBody)).build(),
                responseType);
    }

    // ====== Internals =======================================================

    private Mono<ResponseEntity<String>> exchange(HttpMethod method, ApiRequest request) {
        WebClient.RequestBodySpec spec = webClient.method(method).uri(request.getEndpoint());
        WebClient.RequestHeadersSpec<?> headersSpec = (request.getPayload() != null)
                ? spec.bodyValue(request.getPayload())
                : spec;
        return headersSpec.retrieve().toEntity(String.class);
    }

    private String serialize(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }

    private <T> T deserialize(String data, Class<T> type) {
        try {
            return objectMapper.readValue(data, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize response body", e);
        }
    }
}
