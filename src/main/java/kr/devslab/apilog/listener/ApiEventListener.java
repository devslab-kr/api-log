package kr.devslab.apilog.listener;

import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.service.ApiLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiEventListener {
    private final ApiLogService apiLogService;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleApiCallInitiated(ApiCallInitiatedEvent event) {
        try {
            apiLogService.saveApiCallInitiated(event);
            log.debug("Saved API Call Initiated: RequestId={}, Endpoint={}",
                    event.getRequest().getRequestId(), event.getRequest().getEndpoint());
        } catch (Exception e) {
            log.error("Failed to save API Call Initiated: RequestId={}, Error={}",
                    event.getRequest().getRequestId(), e.getMessage(), e);
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleApiCallSuccess(ApiCallSuccessEvent event) {
        try {
            apiLogService.saveApiCallSuccess(event);
            log.debug("Saved API Call Success: RequestId={}, Endpoint={}, Status={}",
                    event.getRequest().getRequestId(), event.getRequest().getEndpoint(),
                    event.getResponse().getStatusCode());
        } catch (Exception e) {
            log.error("Failed to save API Call Success: RequestId={}, Error={}",
                    event.getRequest().getRequestId(), e.getMessage(), e);
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void handleApiCallError(ApiCallErrorEvent event) {
        try {
            apiLogService.saveApiCallError(event);
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