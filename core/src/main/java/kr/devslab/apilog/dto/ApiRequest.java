package kr.devslab.apilog.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Caller-supplied request descriptor handed to the HTTP client utilities and
 * carried across the event pipeline.
 *
 * <p>{@code requestId} defaults to a fresh UUID so each call has a unique
 * correlation key in {@code api_log}. Override it when multiple calls form a
 * logical group (e.g., a retry sequence) and you want them to share an id.
 *
 * <p>v0.6.0 note — moved from {@code kr.devslab.apilog.model.dto} as part of
 * the multi-module split (the {@code model/} package now belongs to the
 * backend modules, which carry their own entity types).
 */
@Getter
@Builder
public class ApiRequest {
    @Builder.Default
    private final String requestId = UUID.randomUUID().toString();
    private final String payload;
    private final String endpoint;
}
