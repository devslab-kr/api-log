package kr.devslab.apilog.model.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiResponse {
    private final String data;
    private final int statusCode;
}