package kr.devslab.apilog.listener;

import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.spi.ApiLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

/**
 * Drives the {@link ApiLogWriter} off the application event bus.
 *
 * <p>v0.6.0 change — this used to call {@code ApiLogService} directly (JPA-only).
 * It now talks to whichever {@link ApiLogWriter} the consumer's chosen backend
 * registered ({@code api-log-jpa} → JpaApiLogWriter,
 * {@code api-log-r2dbc} → R2dbcApiLogWriter,
 * {@code api-log-mybatis} → MybatisApiLogWriter). The listener stays
 * backend-agnostic; routing happens by which jar is on the classpath.
 *
 * <p>{@code @Retryable} wraps each write in up to three attempts with 1s
 * backoff so transient persistence failures (connection blips, dead pool
 * connection on first use) don't drop a log row. Caught exceptions are logged,
 * never rethrown — losing one {@code api_log} row must never break the actual
 * outbound API call.
 *
 * <p>Transaction semantics are intentionally <i>not</i> declared here. The JPA
 * writer wraps its own {@code save()} in {@code REQUIRES_NEW} (so the log
 * write doesn't pollute the consumer's surrounding tx); the R2DBC and MyBatis
 * writers do the equivalent inside their own implementations. Keeping the
 * tx boundary inside the writer lets each backend pick the semantics that
 * make sense (e.g., R2DBC has no @Transactional support out of the box).
 */
@Slf4j
@RequiredArgsConstructor
public class ApiEventListener {

    private final ApiLogWriter writer;

    @EventListener
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleApiCallInitiated(ApiCallInitiatedEvent event) {
        try {
            writer.writeInitiated(event);
            log.debug("Saved API Call Initiated: RequestId={}, Endpoint={}",
                    event.getRequest().getRequestId(), event.getRequest().getEndpoint());
        } catch (Exception e) {
            log.error("Failed to save API Call Initiated: RequestId={}, Error={}",
                    event.getRequest().getRequestId(), e.getMessage(), e);
        }
    }

    @EventListener
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleApiCallSuccess(ApiCallSuccessEvent event) {
        try {
            writer.writeSuccess(event);
            log.debug("Saved API Call Success: RequestId={}, Endpoint={}, Status={}",
                    event.getRequest().getRequestId(), event.getRequest().getEndpoint(),
                    event.getResponse().getStatusCode());
        } catch (Exception e) {
            log.error("Failed to save API Call Success: RequestId={}, Error={}",
                    event.getRequest().getRequestId(), e.getMessage(), e);
        }
    }

    @EventListener
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleApiCallError(ApiCallErrorEvent event) {
        try {
            writer.writeError(event);
            log.info("Saved API Call {}: RequestId={}, Endpoint={}, RetryCount={}",
                    event.isRetry() ? "Retry Error" : "Error",
                    event.getRequest().getRequestId(), event.getRequest().getEndpoint(),
                    event.getRetryCount());
        } catch (Exception e) {
            log.error("Failed to save API Call {}: RequestId={}, Error={}",
                    event.isRetry() ? "Retry Error" : "Error",
                    event.getRequest().getRequestId(), e.getMessage(), e);
        }
    }
}
