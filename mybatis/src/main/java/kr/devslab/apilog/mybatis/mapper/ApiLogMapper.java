package kr.devslab.apilog.mybatis.mapper;

import kr.devslab.apilog.mybatis.model.ApiLogRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

import java.util.List;

/**
 * MyBatis mapper for the {@code api_log} table.
 *
 * <p>The {@code ::jsonb} cast on each JSON parameter lets MyBatis bind a Java
 * {@link String} into a PostgreSQL {@code JSONB} column without needing a
 * custom {@code TypeHandler}. {@code #{payload,jdbcType=VARCHAR}} forces the
 * VARCHAR binding even when the value is {@code null}, which side-steps
 * PostgreSQL's "could not determine data type of parameter" error on null
 * JSONB binds.
 *
 * <p>{@code @Options(useGeneratedKeys=true)} flows the {@code BIGSERIAL}
 * {@code id} back onto the {@link ApiLogRow} after insert — handy for tests
 * that want to assert on a specific row even though the writer itself doesn't
 * read it back.
 */
@Mapper
public interface ApiLogMapper {

    @Insert("""
            INSERT INTO api_log
                (event_type, request_id, endpoint, payload, response,
                 status_code, error_message, timestamp, retry_count, is_retry)
            VALUES
                (#{eventType},
                 #{requestId},
                 #{endpoint},
                 CAST(#{payload,jdbcType=VARCHAR} AS jsonb),
                 CAST(#{response,jdbcType=VARCHAR} AS jsonb),
                 #{statusCode,jdbcType=INTEGER},
                 CAST(#{errorMessage,jdbcType=VARCHAR} AS jsonb),
                 #{timestamp},
                 #{retryCount},
                 #{isRetry})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(ApiLogRow row);

    @org.apache.ibatis.annotations.Select(
            "SELECT id, event_type AS eventType, request_id AS requestId, endpoint, " +
            "payload::text AS payload, response::text AS response, status_code AS statusCode, " +
            "error_message::text AS errorMessage, timestamp, retry_count AS retryCount, is_retry AS isRetry " +
            "FROM api_log WHERE request_id = #{requestId} ORDER BY id ASC"
    )
    List<ApiLogRow> findByRequestId(String requestId);
}
