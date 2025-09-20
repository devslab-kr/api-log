package com.devs.lab.test.config;

import com.devs.lab.test.event.ApiCallErrorEvent;
import com.devs.lab.test.event.ApiCallInitiatedEvent;
import com.devs.lab.test.event.ApiCallSuccessEvent;
import com.devs.lab.test.model.dto.ApiRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;

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
                // 성공 시 추가 작업 없음
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
                // 종료 시 추가 작업 없음
            }

            private ApiRequest extractRequest(Object event) {
                if (event instanceof ApiCallInitiatedEvent) {
                    return ((ApiCallInitiatedEvent) event).getRequest();
                } else if (event instanceof ApiCallSuccessEvent) {
                    return ((ApiCallSuccessEvent) event).getRequest();
                } else if (event instanceof ApiCallErrorEvent) {
                    return ((ApiCallErrorEvent) event).getRequest();
                }
                return null;
            }
        };
    }
}