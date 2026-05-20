package kr.devslab.apilog.config;

import kr.devslab.apilog.dto.ApiRequest;
import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Enables Spring Retry across the api-log starter and registers a
 * {@link RetryListener} that re-publishes a {@link ApiCallErrorEvent} marked
 * {@code isRetry=true} on every failed attempt past the first, so each retry
 * is recorded as its own {@code RETRY_ERROR} row in {@code api_log}.
 *
 * <p>Imported by {@code ApiLogCoreAutoConfiguration} so consumers don't need to
 * declare {@code @EnableRetry} on their own {@code @SpringBootApplication}.
 */
@Configuration
@EnableRetry
public class RetryConfig {

    @Bean
    public RetryListener retryListener(ApplicationEventPublisher eventPublisher) {
        return new RetryListener() {
            @Override
            public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
                Object event = context.getAttribute("event");
                if (event != null) {
                    context.setAttribute("request", extractRequest(event));
                }
                return true;
            }

            @Override
            public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
                // no-op
            }

            @Override
            public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                if (context.getRetryCount() > 0) {
                    ApiRequest request = (ApiRequest) context.getAttribute("request");
                    if (request != null) {
                        eventPublisher.publishEvent(new ApiCallErrorEvent(
                                this, request, throwable, context.getRetryCount(), true));
                    }
                }
            }

            @Override
            public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                // no-op
            }

            private ApiRequest extractRequest(Object event) {
                if (event instanceof ApiCallInitiatedEvent initiated) {
                    return initiated.getRequest();
                } else if (event instanceof ApiCallSuccessEvent success) {
                    return success.getRequest();
                } else if (event instanceof ApiCallErrorEvent error) {
                    return error.getRequest();
                }
                return null;
            }
        };
    }
}
