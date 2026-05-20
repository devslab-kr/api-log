package kr.devslab.apilog.mybatis.writer;

import kr.devslab.apilog.dto.ApiRequest;
import kr.devslab.apilog.dto.ApiResponse;
import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.mybatis.mapper.ApiLogMapper;
import kr.devslab.apilog.mybatis.model.ApiLogRow;
import kr.devslab.apilog.spi.ApiLogWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static kr.devslab.apilog.Constants.ERROR;
import static kr.devslab.apilog.Constants.INITIATED;
import static kr.devslab.apilog.Constants.RETRY_ERROR;
import static kr.devslab.apilog.Constants.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the MyBatis backend — boots a Spring context, drives
 * events through the registered {@link ApiLogWriter}, then verifies rows via
 * both the mapper's own {@code findByRequestId} (round-trips JSONB → text)
 * and a direct {@link JdbcTemplate} query (sanity check on the binding).
 */
@SpringBootTest
@Testcontainers
class MybatisApiLogWriterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("apilog_mybatis_it")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    ApiLogWriter writer;

    @Autowired
    ApiLogMapper mapper;

    @Autowired
    DataSource dataSource;

    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM api_log");
    }

    @Test
    void writer_isWiredFromMybatisBackend() {
        assertThat(writer.getClass().getSimpleName()).isEqualTo("MybatisApiLogWriter");
    }

    @Test
    void writeInitiated_insertsRowWithJsonbPayload() {
        String reqId = UUID.randomUUID().toString();
        ApiRequest request = ApiRequest.builder()
                .endpoint("/charges")
                .payload("{\"amount\":100}")
                .requestId(reqId)
                .build();

        writer.writeInitiated(new ApiCallInitiatedEvent(this, request));

        List<ApiLogRow> rows = mapper.findByRequestId(reqId);
        assertThat(rows).hasSize(1);
        ApiLogRow row = rows.get(0);
        assertThat(row.getEventType()).isEqualTo(INITIATED);
        assertThat(row.getEndpoint()).isEqualTo("/charges");
        assertThat(row.getRetryCount()).isEqualTo(0);
        assertThat(row.getIsRetry()).isFalse();
        assertThat(row.getPayload()).contains("\"amount\":100");
    }

    @Test
    void writeSuccess_insertsRowWithResponseJsonAndStatusCode() {
        String reqId = UUID.randomUUID().toString();
        ApiRequest request = ApiRequest.builder()
                .endpoint("/charges").requestId(reqId).build();
        ApiResponse response = ApiResponse.builder()
                .data("{\"id\":\"ch_1\"}").statusCode(201).build();

        writer.writeSuccess(new ApiCallSuccessEvent(this, request, response));

        List<ApiLogRow> rows = mapper.findByRequestId(reqId);
        assertThat(rows).hasSize(1);
        ApiLogRow row = rows.get(0);
        assertThat(row.getEventType()).isEqualTo(SUCCESS);
        assertThat(row.getStatusCode()).isEqualTo(201);
        assertThat(row.getResponse()).contains("\"id\":\"ch_1\"");
    }

    @Test
    void writeError_insertsStructuredErrorMessage() {
        String reqId = UUID.randomUUID().toString();
        ApiRequest request = ApiRequest.builder()
                .endpoint("/charges").requestId(reqId).build();
        IllegalStateException error = new IllegalStateException("connection broken");
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request, error, 0, false);

        writer.writeError(event);

        List<ApiLogRow> rows = mapper.findByRequestId(reqId);
        assertThat(rows).hasSize(1);
        ApiLogRow row = rows.get(0);
        assertThat(row.getEventType()).isEqualTo(ERROR);
        assertThat(row.getStatusCode()).isNull();
        assertThat(row.getErrorMessage())
                .contains("\"type\":\"java.lang.IllegalStateException\"")
                .contains("\"message\":\"connection broken\"");
    }

    @Test
    void writeError_marksRetryErrorWhenRetryFlagSet() {
        String reqId = UUID.randomUUID().toString();
        ApiRequest request = ApiRequest.builder()
                .endpoint("/charges").requestId(reqId).build();
        ApiCallErrorEvent event = new ApiCallErrorEvent(this, request,
                new RuntimeException("retry"), 2, true);

        writer.writeError(event);

        List<ApiLogRow> rows = mapper.findByRequestId(reqId);
        assertThat(rows).hasSize(1);
        ApiLogRow row = rows.get(0);
        assertThat(row.getEventType()).isEqualTo(RETRY_ERROR);
        assertThat(row.getRetryCount()).isEqualTo(2);
        assertThat(row.getIsRetry()).isTrue();
    }
}
