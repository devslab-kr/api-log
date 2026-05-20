package kr.devslab.apilog.r2dbc.writer;

import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.spi.ApiLogWriter;
import kr.devslab.apilog.spi.HttpErrorExtractor;
import kr.devslab.apilog.spi.HttpErrorInfo;
import kr.devslab.apilog.spi.PayloadJsonMapper;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

import static kr.devslab.apilog.Constants.ERROR;
import static kr.devslab.apilog.Constants.INITIATED;
import static kr.devslab.apilog.Constants.RETRY_ERROR;
import static kr.devslab.apilog.Constants.SUCCESS;

/**
 * Reactive (R2DBC) implementation of {@link ApiLogWriter}.
 *
 * <p>Talks to a {@link DatabaseClient} directly rather than going through a
 * Spring Data R2DBC repository — this keeps the dependency footprint small
 * (no spring-data-r2dbc, no Spring Data Commons), and gives us a clean place
 * to do the explicit {@code ::jsonb} cast PostgreSQL needs when binding a
 * {@code TEXT} parameter into a {@code JSONB} column.
 *
 * <p>The writer subscribes to the resulting {@code Mono} inline via
 * {@code .subscribe()} so it matches the fire-and-forget semantics the core
 * listener expects (the listener doesn't consume return values, and the
 * surrounding {@code @Async} executor wouldn't propagate a {@code Mono}
 * usefully anyway).
 *
 * <p>Subscription errors are logged but not rethrown — losing one audit row
 * must never break the consumer's actual outbound HTTP call. Same contract
 * as the JPA writer.
 */
@Slf4j
@RequiredArgsConstructor
public class R2dbcApiLogWriter implements ApiLogWriter {

    private static final String INSERT_SQL = """
            INSERT INTO api_log
                (event_type, request_id, endpoint, payload, response,
                 status_code, error_message, timestamp, retry_count, is_retry)
            VALUES
                (:eventType, :requestId, :endpoint, :payload, :response,
                 :statusCode, :errorMessage, :timestamp, :retryCount, :isRetry)
            """;

    private final DatabaseClient databaseClient;
    private final PayloadJsonMapper jsonMapper;

    @Override
    public void writeInitiated(ApiCallInitiatedEvent event) {
        executeInsert(
                INITIATED,
                event.getRequest().getRequestId(),
                event.getRequest().getEndpoint(),
                jsonMapper.toJsonString(event.getRequest().getPayload()),
                null,
                null,
                null,
                0,
                false
        );
    }

    @Override
    public void writeSuccess(ApiCallSuccessEvent event) {
        executeInsert(
                SUCCESS,
                event.getRequest().getRequestId(),
                event.getRequest().getEndpoint(),
                jsonMapper.toJsonString(event.getRequest().getPayload()),
                jsonMapper.toJsonString(event.getResponse().getData()),
                event.getResponse().getStatusCode(),
                null,
                0,
                false
        );
    }

    @Override
    public void writeError(ApiCallErrorEvent event) {
        Throwable error = event.getError();
        HttpErrorInfo info = HttpErrorExtractor.extract(error);

        executeInsert(
                event.isRetry() ? RETRY_ERROR : ERROR,
                event.getRequest().getRequestId(),
                event.getRequest().getEndpoint(),
                jsonMapper.toJsonString(event.getRequest().getPayload()),
                null,
                info.statusCode(),
                jsonMapper.buildErrorJsonString(error, info.responseBody()),
                event.getRetryCount(),
                event.isRetry()
        );
    }

    /**
     * Common write path — every event type funnels through this.
     *
     * <p>Each JSONB parameter is bound as a {@link R2dbcType#CLOB CLOB} via
     * {@link Parameters#in(io.r2dbc.spi.Type, Object)} and the driver hands it
     * to PostgreSQL as text. The PostgreSQL R2DBC driver applies the column's
     * implicit cast (TEXT → JSONB) on insert, so no manual {@code ::jsonb} is
     * needed — keeps the SQL portable to other dialects that may follow.
     */
    private void executeInsert(String eventType,
                                String requestId,
                                String endpoint,
                                String payload,
                                String response,
                                Integer statusCode,
                                String errorMessage,
                                int retryCount,
                                boolean isRetry) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(INSERT_SQL)
                .bind("eventType", eventType)
                .bind("requestId", requestId)
                .bind("endpoint", endpoint)
                .bind("payload", asJsonbParam(payload))
                .bind("response", asJsonbParam(response))
                .bind("statusCode", statusCode == null
                        ? Parameters.in(R2dbcType.INTEGER)
                        : Parameters.in(R2dbcType.INTEGER, statusCode))
                .bind("errorMessage", asJsonbParam(errorMessage))
                .bind("timestamp", LocalDateTime.now())
                .bind("retryCount", retryCount)
                .bind("isRetry", isRetry);

        // Fire-and-forget — the whole point of this backend is to NOT block
        // the caller's reactor thread. Pinning the subscription to
        // boundedElastic guarantees the insert actually runs on a worker thread
        // (without it the chain can starve when the caller hands control
        // straight back to a CPU-bound test loop or a single-core CI runner —
        // which is what bit the v0.6.0 first integration run).
        spec.fetch()
                .rowsUpdated()
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(rows -> log.debug(
                        "R2DBC api_log insert ok: requestId={}, eventType={}, rows={}",
                        requestId, eventType, rows))
                .doOnError(ex -> log.error(
                        "R2DBC api_log insert failed: requestId={}, eventType={}",
                        requestId, eventType, ex))
                .subscribe();
    }

    private static Object asJsonbParam(String value) {
        // CLOB binding triggers the driver's TEXT path. Passing null with a
        // typed Parameters.in(...) preserves the NULL JSONB semantics — a raw
        // null would let the driver guess the type and bind it as untyped NULL.
        return value == null
                ? Parameters.in(R2dbcType.CLOB)
                : Parameters.in(R2dbcType.CLOB, value);
    }
}
