package kr.devslab.apilog.mybatis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Plain row carrier handed to the MyBatis mapper. Mirrors the {@code api_log}
 * table — the JPA backend's {@code ApiLogEntity} has the same shape but
 * carries the Hibernate {@code @JdbcTypeCode(JSON)} annotations; here we
 * keep it framework-free since MyBatis handles the parameter binding via the
 * {@code ::jsonb} cast in the mapper SQL.
 *
 * <p>The {@code payload}, {@code response}, and {@code errorMessage} fields
 * are JSON strings (canonical form produced by {@code PayloadJsonMapper}) —
 * the mapper SQL casts them to JSONB on insert.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiLogRow {
    private Long id;
    private String eventType;
    private String requestId;
    private String endpoint;
    private String payload;
    private String response;
    private Integer statusCode;
    private String errorMessage;
    private LocalDateTime timestamp;
    private Integer retryCount;
    private Boolean isRetry;
}
