package kr.devslab.apilog.jpa.writer;

import kr.devslab.apilog.dto.ApiRequest;
import kr.devslab.apilog.dto.ApiResponse;
import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.jpa.model.ApiLogEntity;
import kr.devslab.apilog.jpa.repository.ApiLogRepository;
import kr.devslab.apilog.spi.PayloadJsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static kr.devslab.apilog.Constants.ERROR;
import static kr.devslab.apilog.Constants.INITIATED;
import static kr.devslab.apilog.Constants.RETRY_ERROR;
import static kr.devslab.apilog.Constants.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link JpaApiLogWriter}. The repository is mocked so we can
 * assert exactly what the writer produces for each event type without
 * spinning up a database.
 *
 * <p>v0.6.0 — this is the same coverage that used to live in
 * {@code ApiLogServiceTest}, but pointed at the new writer interface.
 */
@ExtendWith(MockitoExtension.class)
class JpaApiLogWriterTest {

    @Mock
    private ApiLogRepository repository;

    private JpaApiLogWriter writer;
    private ArgumentCaptor<ApiLogEntity> entityCaptor;

    @BeforeEach
    void setUp() {
        writer = new JpaApiLogWriter(repository, new PayloadJsonMapper(new ObjectMapper()));
        entityCaptor = ArgumentCaptor.forClass(ApiLogEntity.class);
    }

    @Test
    void writeInitiated_savesEntityWithCorrectData() {
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("{\"test\":\"data\"}")
                .build();

        writer.writeInitiated(new ApiCallInitiatedEvent(this, request));

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
    void writeSuccess_savesEntityWithResponseData() {
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("{\"test\":\"data\"}")
                .build();
        ApiResponse response = ApiResponse.builder()
                .data("{\"result\":\"success\"}")
                .statusCode(200)
                .build();

        writer.writeSuccess(new ApiCallSuccessEvent(this, request, response));

        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();
        assertThat(saved.getEventType()).isEqualTo(SUCCESS);
        assertThat(saved.getStatusCode()).isEqualTo(200);
    }

    @Test
    void writeError_savesErrorWithRetryFlag() {
        ApiRequest request = ApiRequest.builder().endpoint("/api/test").build();
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request,
                new RuntimeException("Test error"), 1, false);

        writer.writeError(event);

        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();
        assertThat(saved.getEventType()).isEqualTo(ERROR);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getIsRetry()).isFalse();
    }

    @Test
    void writeError_marksRetryErrorWhenRetryFlagSet() {
        ApiRequest request = ApiRequest.builder().endpoint("/api/test").build();
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request,
                new RuntimeException("Retry error"), 2, true);

        writer.writeError(event);

        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();
        assertThat(saved.getEventType()).isEqualTo(RETRY_ERROR);
        assertThat(saved.getRetryCount()).isEqualTo(2);
        assertThat(saved.getIsRetry()).isTrue();
    }

    @Test
    void writeInitiated_handlesNullPayload() {
        ApiRequest request = ApiRequest.builder().endpoint("/api/test").build();

        writer.writeInitiated(new ApiCallInitiatedEvent(this, request));

        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();
        assertThat(saved.getPayload()).isNotNull(); // empty ObjectNode, not null
    }

    @Test
    void writeError_writesStructuredErrorMessage() {
        ApiRequest request = ApiRequest.builder().endpoint("/api/test").build();
        IllegalStateException error = new IllegalStateException("connection broken");

        writer.writeError(new ApiCallErrorEvent(this, request, error, 0, false));

        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();
        assertThat(saved.getErrorMessage()).isNotNull();
        assertThat(saved.getErrorMessage().get("type").asText())
                .isEqualTo("java.lang.IllegalStateException");
        assertThat(saved.getErrorMessage().get("message").asText())
                .isEqualTo("connection broken");
        assertThat(saved.getErrorMessage().has("responseBody")).isFalse();
        assertThat(saved.getStatusCode()).isNull();
    }

    @Test
    void writeError_extractsHttpStatusAndResponseBody() {
        ApiRequest request = ApiRequest.builder().endpoint("/api/test").build();
        HttpClientErrorException error = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                org.springframework.http.HttpHeaders.EMPTY,
                "{\"error\":\"user not found\"}".getBytes(),
                null
        );

        writer.writeError(new ApiCallErrorEvent(this, request, error, 0, false));

        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();
        assertThat(saved.getStatusCode()).isEqualTo(404);
        assertThat(saved.getErrorMessage().get("type").asText())
                .contains("HttpClientErrorException");
        assertThat(saved.getErrorMessage().get("responseBody").asText())
                .isEqualTo("{\"error\":\"user not found\"}");
    }

    @Test
    void writeInitiated_handlesInvalidJson() {
        ApiRequest request = ApiRequest.builder()
                .endpoint("/api/test")
                .payload("invalid json {")
                .build();

        writer.writeInitiated(new ApiCallInitiatedEvent(this, request));

        verify(repository).save(entityCaptor.capture());
        ApiLogEntity saved = entityCaptor.getValue();
        assertThat(saved.getPayload()).isNotNull();
        assertThat(saved.getPayload().has("raw")).isTrue();
    }
}
