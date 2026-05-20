package kr.devslab.apilog.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Wrapper for the response body + HTTP status code emitted by the HTTP client
 * utilities. Stored verbatim in {@code api_log.response} (body) +
 * {@code api_log.status_code} on success.
 */
@Getter
@Builder
public class ApiResponse {
    private final String data;
    private final int statusCode;
}
