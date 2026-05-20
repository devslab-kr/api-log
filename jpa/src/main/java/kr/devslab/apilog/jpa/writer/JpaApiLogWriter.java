package kr.devslab.apilog.jpa.writer;

import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.jpa.model.ApiLogEntity;
import kr.devslab.apilog.jpa.repository.ApiLogRepository;
import kr.devslab.apilog.spi.ApiLogWriter;
import kr.devslab.apilog.spi.HttpErrorExtractor;
import kr.devslab.apilog.spi.HttpErrorInfo;
import kr.devslab.apilog.spi.PayloadJsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static kr.devslab.apilog.Constants.ERROR;
import static kr.devslab.apilog.Constants.INITIATED;
import static kr.devslab.apilog.Constants.RETRY_ERROR;
import static kr.devslab.apilog.Constants.SUCCESS;

/**
 * JPA implementation of {@link ApiLogWriter}. Persists every event as a new
 * row in the {@code api_log} table.
 *
 * <p>Each write runs in {@link Propagation#REQUIRES_NEW} so the audit write
 * never participates in (and never breaks) the consumer's outer transaction —
 * a rollback in the caller's business code mustn't erase log rows for calls
 * that already happened.
 *
 * <p>v0.6.0 — this is the same logic that lived in the old
 * {@code kr.devslab.apilog.service.ApiLogService}, now repackaged as a writer
 * and exposed via the {@link ApiLogWriter} SPI so the core listener can talk
 * to it without an import cycle.
 */
@RequiredArgsConstructor
public class JpaApiLogWriter implements ApiLogWriter {

    private final ApiLogRepository repository;
    private final PayloadJsonMapper jsonMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInitiated(ApiCallInitiatedEvent event) {
        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(INITIATED)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(jsonMapper.toJsonNode(event.getRequest().getPayload()))
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();
        repository.save(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeSuccess(ApiCallSuccessEvent event) {
        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(SUCCESS)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(jsonMapper.toJsonNode(event.getRequest().getPayload()))
                .response(jsonMapper.toJsonNode(event.getResponse().getData()))
                .statusCode(event.getResponse().getStatusCode())
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();
        repository.save(entity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeError(ApiCallErrorEvent event) {
        Throwable error = event.getError();
        HttpErrorInfo info = HttpErrorExtractor.extract(error);

        ApiLogEntity entity = ApiLogEntity.builder()
                .eventType(event.isRetry() ? RETRY_ERROR : ERROR)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(jsonMapper.toJsonNode(event.getRequest().getPayload()))
                .errorMessage(jsonMapper.buildErrorJson(error, info.responseBody()))
                .statusCode(info.statusCode())
                .timestamp(LocalDateTime.now())
                .retryCount(event.getRetryCount())
                .isRetry(event.isRetry())
                .build();
        repository.save(entity);
    }
}
