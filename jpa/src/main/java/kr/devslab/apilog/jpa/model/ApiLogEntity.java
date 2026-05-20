package kr.devslab.apilog.jpa.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA entity mapping for the {@code api_log} table.
 *
 * <p>The three JSONB columns ({@code payload}, {@code response},
 * {@code error_message}) use Hibernate's {@code @JdbcTypeCode(SqlTypes.JSON)}
 * which delegates to the PostgreSQL dialect's JSONB binder. The corresponding
 * R2DBC + MyBatis backends store the same columns differently
 * (Json type / TypeHandler).
 *
 * <p>v0.6.0 — moved from {@code kr.devslab.apilog.model} to
 * {@code kr.devslab.apilog.jpa.model} as part of the multi-module split.
 * Consumers who imported {@code ApiLogEntity} directly will need to update
 * their import.
 */
@Entity
@Table(name = "api_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "request_id")
    private String requestId;

    private String endpoint;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode response;

    @Column(name = "status_code")
    private Integer statusCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_message")
    private JsonNode errorMessage;

    private LocalDateTime timestamp;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "is_retry")
    private Boolean isRetry;
}
