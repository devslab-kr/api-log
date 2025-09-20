package com.devs.lab.test.model;

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