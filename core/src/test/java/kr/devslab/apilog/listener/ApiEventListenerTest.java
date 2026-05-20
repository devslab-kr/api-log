package kr.devslab.apilog.listener;

import kr.devslab.apilog.dto.ApiRequest;
import kr.devslab.apilog.dto.ApiResponse;
import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.spi.ApiLogWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Verifies the listener routes events through whatever {@link ApiLogWriter}
 * bean is injected — the v0.6.0 SPI seam. The writer is a {@link RecordingWriter}
 * that captures calls; tests assert ordering / arguments / fault-tolerance.
 *
 * <p>The {@code @Retryable} behavior is exercised in a separate Spring-context
 * test in the {@code :jpa} module — here we only need the writer wiring to
 * work, so no Spring context.
 */
class ApiEventListenerTest {

    private RecordingWriter writer;
    private ApiEventListener listener;

    @BeforeEach
    void setUp() {
        writer = new RecordingWriter();
        listener = new ApiEventListener(writer);
    }

    @Test
    void initiatedEvent_routesToWriter() {
        ApiRequest request = ApiRequest.builder()
                .endpoint("/x")
                .payload("{}")
                .requestId("r-1")
                .build();

        listener.handleApiCallInitiated(new ApiCallInitiatedEvent(this, request));

        assertThat(writer.initiated).hasSize(1);
        assertThat(writer.initiated.get(0).getRequest().getRequestId()).isEqualTo("r-1");
    }

    @Test
    void successEvent_routesToWriter() {
        ApiRequest request = ApiRequest.builder().endpoint("/x").requestId("r-2").build();
        ApiResponse response = ApiResponse.builder().statusCode(201).data("{}").build();

        listener.handleApiCallSuccess(new ApiCallSuccessEvent(this, request, response));

        assertThat(writer.success).hasSize(1);
        assertThat(writer.success.get(0).getResponse().getStatusCode()).isEqualTo(201);
    }

    @Test
    void errorEvent_routesToWriter_andCarriesRetryFlag() {
        ApiRequest request = ApiRequest.builder().endpoint("/x").requestId("r-3").build();
        RuntimeException boom = new RuntimeException("boom");

        listener.handleApiCallError(new ApiCallErrorEvent(this, request, boom, 2, true));

        assertThat(writer.errors).hasSize(1);
        assertThat(writer.errors.get(0).isRetry()).isTrue();
        assertThat(writer.errors.get(0).getRetryCount()).isEqualTo(2);
    }

    @Test
    void writerThrows_listenerSwallows_soOutboundCallIsNotBroken() {
        // The whole point of catching inside the listener: losing one audit row
        // must never propagate up and break the consumer's outbound API call.
        ApiLogWriter exploding = mock(ApiLogWriter.class);
        doThrow(new RuntimeException("db down"))
                .when(exploding).writeSuccess(org.mockito.ArgumentMatchers.any());

        ApiEventListener fragileListener = new ApiEventListener(exploding);
        ApiRequest request = ApiRequest.builder().endpoint("/x").requestId("r-4").build();
        ApiResponse response = ApiResponse.builder().statusCode(200).build();

        // Must not throw.
        fragileListener.handleApiCallSuccess(new ApiCallSuccessEvent(this, request, response));
    }

    // ------------------------------------------------------------------ //
    // Test doubles                                                         //
    // ------------------------------------------------------------------ //

    static class RecordingWriter implements ApiLogWriter {
        final List<ApiCallInitiatedEvent> initiated = new ArrayList<>();
        final List<ApiCallSuccessEvent> success = new ArrayList<>();
        final List<ApiCallErrorEvent> errors = new ArrayList<>();

        @Override
        public void writeInitiated(ApiCallInitiatedEvent event) {
            initiated.add(event);
        }

        @Override
        public void writeSuccess(ApiCallSuccessEvent event) {
            success.add(event);
        }

        @Override
        public void writeError(ApiCallErrorEvent event) {
            errors.add(event);
        }
    }
}
