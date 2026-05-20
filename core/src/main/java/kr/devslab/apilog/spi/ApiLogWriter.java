package kr.devslab.apilog.spi;

import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;

/**
 * Backend-agnostic SPI that each {@code api-log-*} persistence module implements.
 *
 * <p>One {@code ApiLogWriter} bean is expected per application context — provided
 * by the backend artifact the consumer chose (see {@code api-log-jpa},
 * {@code api-log-r2dbc}, {@code api-log-mybatis}). The core listener
 * ({@code ApiEventListener}) routes every event through the registered writer
 * so the wire format (events) stays backend-independent.
 *
 * <p><b>Append-only semantics.</b> Every call writes a new row keyed by an
 * auto-generated {@code id}. The same {@code request_id} can show up multiple
 * times — once for {@code INITIATED}, once for {@code SUCCESS} or {@code ERROR},
 * and once per {@code RETRY_ERROR} — because the table is a chronological log,
 * not a state machine.
 *
 * <p><b>Threading.</b> Calls arrive on the executor configured in {@code :core}
 * (virtual threads by default; falls back to a platform thread pool). Reactive
 * implementations may subscribe inline — the listener does not consume any
 * returned reactive type, so writers own their own subscription lifecycle.
 */
public interface ApiLogWriter {

    /** Persist an {@code INITIATED} row when a request leaves the client. */
    void writeInitiated(ApiCallInitiatedEvent event);

    /** Persist a {@code SUCCESS} row when a 2xx response arrives. */
    void writeSuccess(ApiCallSuccessEvent event);

    /**
     * Persist an {@code ERROR} or {@code RETRY_ERROR} row when the call fails.
     * The {@code event_type} is selected from {@link ApiCallErrorEvent#isRetry()}.
     */
    void writeError(ApiCallErrorEvent event);
}
