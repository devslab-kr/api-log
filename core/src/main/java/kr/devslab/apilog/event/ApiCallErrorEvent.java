package kr.devslab.apilog.event;

import kr.devslab.apilog.dto.ApiRequest;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Fired when an HTTP call fails (exception thrown by RestClient / WebClient,
 * or a non-2xx response surfaced as an exception).
 *
 * <p>{@code retryCount} + {@code isRetry} let downstream consumers distinguish
 * a first-attempt failure ({@code ERROR}) from a retry failure
 * ({@code RETRY_ERROR}).
 */
@Getter
public class ApiCallErrorEvent extends ApplicationEvent {
    private final ApiRequest request;
    private final Throwable error;
    private final LocalDateTime eventTimestamp;
    private final int retryCount;
    private final boolean isRetry;

    public ApiCallErrorEvent(Object source, ApiRequest request, Throwable error, int retryCount, boolean isRetry) {
        super(source);
        this.request = request;
        this.error = error;
        this.eventTimestamp = LocalDateTime.now();
        this.retryCount = retryCount;
        this.isRetry = isRetry;
    }
}
