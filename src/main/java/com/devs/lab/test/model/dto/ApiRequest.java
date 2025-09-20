package com.devs.lab.test.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ApiRequest {
    @Builder.Default
    private final String requestId = UUID.randomUUID().toString();
    private final String payload;
    private final String endpoint;
}