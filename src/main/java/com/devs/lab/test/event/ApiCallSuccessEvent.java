package com.devs.lab.test.event;

import com.devs.lab.test.model.dto.ApiRequest;
import com.devs.lab.test.model.dto.ApiResponse;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

@Getter
public class ApiCallSuccessEvent extends ApplicationEvent {
    private final ApiRequest request;
    private final ApiResponse response;
    private final LocalDateTime eventTimestamp;

    public ApiCallSuccessEvent(Object source, ApiRequest request, ApiResponse response) {
        super(source);
        this.request = request;
        this.response = response;
        this.eventTimestamp = LocalDateTime.now();
    }

}