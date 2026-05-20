package kr.devslab.apilog.service;

import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.model.ApiLogEntity;
import kr.devslab.apilog.repository.ApiLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;

import static kr.devslab.apilog.Constants.ERROR;
import static kr.devslab.apilog.Constants.INITIATED;
import static kr.devslab.apilog.Constants.RETRY_ERROR;
import static kr.devslab.apilog.Constants.SUCCESS;

@Service
@RequiredArgsConstructor
public class ApiLogService {

    private final ApiLogRepository repository;
    private final ObjectMapper objectMapper;

    public void saveApiCallInitiated(ApiCallInitiatedEvent event) {
        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(INITIATED)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(toJsonNode(event.getRequest().getPayload()))
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();
        repository.save(entity);
    }

    public void saveApiCallSuccess(ApiCallSuccessEvent event) {
        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(SUCCESS)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(toJsonNode(event.getRequest().getPayload()))
                .response(toJsonNode(event.getResponse().getData()))
                .statusCode(event.getResponse().getStatusCode())
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();
        repository.save(entity);
    }

    public void saveApiCallError(ApiCallErrorEvent event) {
        Throwable error = event.getError();
        HttpErrorInfo info = extractHttpErrorInfo(error);

        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(event.isRetry() ? RETRY_ERROR : ERROR)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(toJsonNode(event.getRequest().getPayload()))
                .errorMessage(buildErrorJson(error, info.responseBody()))
                .statusCode(info.statusCode())
                .timestamp(LocalDateTime.now())
                .retryCount(event.getRetryCount())
                .isRetry(event.isRetry())
                .build();
        repository.save(entity);
    }

    private record HttpErrorInfo(Integer statusCode, String responseBody) {
        static final HttpErrorInfo EMPTY = new HttpErrorInfo(null, null);
    }

    /**
     * Pulls HTTP status + response body off a throwable when applicable.
     *
     * <p>Direct {@code instanceof} works for the Spring Web (blocking)
     * hierarchy because we depend on {@code spring-web} unconditionally. For
     * Spring WebFlux's {@code WebClientResponseException}, we duck-type via
     * reflection — {@code spring-webflux} is an optional dependency on this
     * starter, so we can't import its types directly without forcing it onto
     * the classpath of consumers who only want the blocking client.
     */
    private HttpErrorInfo extractHttpErrorInfo(Throwable error) {
        if (error instanceof HttpStatusCodeException ex) {
            return new HttpErrorInfo(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        }
        if (error instanceof RestClientResponseException ex) {
            return new HttpErrorInfo(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        }
        // Match WebClientResponseException + its concrete subclasses (NotFound,
        // BadRequest, etc.) by package prefix so unrelated exceptions that
        // happen to share method names don't match.
        if (error.getClass().getName()
                .startsWith("org.springframework.web.reactive.function.client.WebClientResponseException")) {
            try {
                Object status = error.getClass().getMethod("getStatusCode").invoke(error);
                Integer statusValue = (Integer) status.getClass().getMethod("value").invoke(status);
                Object body = error.getClass().getMethod("getResponseBodyAsString").invoke(error);
                return new HttpErrorInfo(statusValue, body == null ? null : body.toString());
            } catch (ReflectiveOperationException ignored) {
                // Shape didn't match — fall through to EMPTY.
            }
        }
        return HttpErrorInfo.EMPTY;
    }

    /**
     * Build the structured error JSON written into the {@code error_message} column.
     *
     * <p>Shape: <pre>{ "type": "<fqcn>", "message": "<exception message>" [, "responseBody": "..."] }</pre>
     *
     * <p>The {@code responseBody} field is only present when the cause was a Spring
     * {@code HttpStatusCodeException} / {@code RestClientResponseException} carrying
     * the upstream's body — useful for diagnosing vendor errors that put detail in
     * the body, not the message.
     */
    private JsonNode buildErrorJson(Throwable error, String responseBody) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", error.getClass().getName());
        node.put("message", error.getMessage());
        if (responseBody != null && !responseBody.isEmpty()) {
            node.put("responseBody", responseBody);
        }
        return node;
    }

    private JsonNode toJsonNode(String data) {
        if (data == null) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(data);
        } catch (Exception e) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("raw", data);
            return node;
        }
    }
}
