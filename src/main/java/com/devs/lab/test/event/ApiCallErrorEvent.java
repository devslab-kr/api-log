package com.devs.lab.test.event;

import com.devs.lab.test.model.dto.ApiRequest;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

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