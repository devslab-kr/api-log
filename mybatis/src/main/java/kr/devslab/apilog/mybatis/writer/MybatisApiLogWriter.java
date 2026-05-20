package kr.devslab.apilog.mybatis.writer;

import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.mybatis.mapper.ApiLogMapper;
import kr.devslab.apilog.mybatis.model.ApiLogRow;
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
 * MyBatis implementation of {@link ApiLogWriter}.
 *
 * <p>Each write runs in {@link Propagation#REQUIRES_NEW} so the audit insert
 * doesn't piggy-back on (or roll back with) the consumer's outer transaction
 * — same contract as {@code JpaApiLogWriter}.
 *
 * <p>JSONB columns are handled by the mapper SQL itself
 * ({@code CAST(#{...,jdbcType=VARCHAR} AS jsonb)}), so this class just builds
 * an {@link ApiLogRow} with string-typed JSON and hands it off.
 */
@RequiredArgsConstructor
public class MybatisApiLogWriter implements ApiLogWriter {

    private final ApiLogMapper mapper;
    private final PayloadJsonMapper jsonMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInitiated(ApiCallInitiatedEvent event) {
        ApiLogRow row = ApiLogRow.builder()
                .eventType(INITIATED)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(jsonMapper.toJsonString(event.getRequest().getPayload()))
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();
        mapper.insert(row);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeSuccess(ApiCallSuccessEvent event) {
        ApiLogRow row = ApiLogRow.builder()
                .eventType(SUCCESS)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(jsonMapper.toJsonString(event.getRequest().getPayload()))
                .response(jsonMapper.toJsonString(event.getResponse().getData()))
                .statusCode(event.getResponse().getStatusCode())
                .timestamp(LocalDateTime.now())
                .retryCount(0)
                .isRetry(false)
                .build();
        mapper.insert(row);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeError(ApiCallErrorEvent event) {
        Throwable error = event.getError();
        HttpErrorInfo info = HttpErrorExtractor.extract(error);

        ApiLogRow row = ApiLogRow.builder()
                .eventType(event.isRetry() ? RETRY_ERROR : ERROR)
                .requestId(event.getRequest().getRequestId())
                .endpoint(event.getRequest().getEndpoint())
                .payload(jsonMapper.toJsonString(event.getRequest().getPayload()))
                .errorMessage(jsonMapper.buildErrorJsonString(error, info.responseBody()))
                .statusCode(info.statusCode())
                .timestamp(LocalDateTime.now())
                .retryCount(event.getRetryCount())
                .isRetry(event.isRetry())
                .build();
        mapper.insert(row);
    }
}
