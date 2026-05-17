package kr.devslab.apilog.util;

import kr.devslab.apilog.model.dto.ApiRequest;
import kr.devslab.apilog.model.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the v0.5.0 convenience methods (PUT, DELETE, PATCH plus the
 * pre-existing GET/POST wrappers) route through the core {@code send*} methods
 * with the correct {@link HttpMethod} and a properly-built {@link ApiRequest}.
 *
 * <p>Doesn't need a real HTTP server or Spring context — we subclass and
 * intercept the core methods to record arguments.
 */
class RestApiClientUtilRoutingTest {

    @Test
    void getSync_routesToGet_withoutPayload() {
        RecordingClient util = new RecordingClient();
        util.getSync("/users/1");
        assertThat(util.lastMethod).isEqualTo(HttpMethod.GET);
        assertThat(util.lastRequest.getEndpoint()).isEqualTo("/users/1");
        assertThat(util.lastRequest.getPayload()).isNull();
    }

    @Test
    void postSync_routesToPost_withStringPayload() {
        RecordingClient util = new RecordingClient();
        util.postSync("/users", "{\"name\":\"Ada\"}");
        assertThat(util.lastMethod).isEqualTo(HttpMethod.POST);
        assertThat(util.lastRequest.getPayload()).isEqualTo("{\"name\":\"Ada\"}");
    }

    @Test
    void postSyncTyped_serializesObjectBodyToJson() {
        RecordingClient util = new RecordingClient();
        var body = new TestUser("Ada", "ada@example.com");
        util.postSyncTyped("/users", body, TestUser.class);
        assertThat(util.lastMethod).isEqualTo(HttpMethod.POST);
        // Body went through Jackson, came out as JSON.
        assertThat(util.lastRequest.getPayload()).contains("\"name\":\"Ada\"");
        assertThat(util.lastRequest.getPayload()).contains("\"email\":\"ada@example.com\"");
    }

    @Test
    void putSync_routesToPut() {
        RecordingClient util = new RecordingClient();
        util.putSync("/users/1", "{\"name\":\"Renamed\"}");
        assertThat(util.lastMethod).isEqualTo(HttpMethod.PUT);
        assertThat(util.lastRequest.getEndpoint()).isEqualTo("/users/1");
        assertThat(util.lastRequest.getPayload()).isEqualTo("{\"name\":\"Renamed\"}");
    }

    @Test
    void putSyncTyped_routesToPutWithSerializedBody() {
        RecordingClient util = new RecordingClient();
        util.putSyncTyped("/users/1", new TestUser("Ada", "ada@example.com"), TestUser.class);
        assertThat(util.lastMethod).isEqualTo(HttpMethod.PUT);
        assertThat(util.lastRequest.getPayload()).contains("\"name\":\"Ada\"");
    }

    @Test
    void deleteSync_routesToDelete_withoutPayload() {
        RecordingClient util = new RecordingClient();
        util.deleteSync("/users/1");
        assertThat(util.lastMethod).isEqualTo(HttpMethod.DELETE);
        assertThat(util.lastRequest.getEndpoint()).isEqualTo("/users/1");
        assertThat(util.lastRequest.getPayload()).isNull();
    }

    @Test
    void patchSync_routesToPatch() {
        RecordingClient util = new RecordingClient();
        util.patchSync("/users/1", "{\"email\":\"new@example.com\"}");
        assertThat(util.lastMethod).isEqualTo(HttpMethod.PATCH);
        assertThat(util.lastRequest.getPayload()).isEqualTo("{\"email\":\"new@example.com\"}");
    }

    @Test
    void patchSyncTyped_routesToPatchWithSerializedBody() {
        RecordingClient util = new RecordingClient();
        util.patchSyncTyped("/users/1", new TestUser("Ada", null), TestUser.class);
        assertThat(util.lastMethod).isEqualTo(HttpMethod.PATCH);
        assertThat(util.lastRequest.getPayload()).contains("\"name\":\"Ada\"");
    }

    @Test
    void sendCore_respectsCallerProvidedRequestId() {
        // The whole point of the v0.5.0 core API: same requestId across retries.
        RecordingClient util = new RecordingClient();
        ApiRequest req = ApiRequest.builder()
                .endpoint("/users")
                .payload("{}")
                .requestId("explicit-retry-correlation-id")
                .build();
        util.send(HttpMethod.POST, req);
        assertThat(util.lastRequest.getRequestId()).isEqualTo("explicit-retry-correlation-id");
    }

    @Test
    void getAsync_routesToGet() {
        RecordingClient util = new RecordingClient();
        CompletableFuture<ApiResponse> future = util.getAsync("/users/1");
        // The CompletableFuture.supplyAsync wraps the call, but INITIATED fires synchronously.
        // Our recording stub completes the future immediately.
        future.join();
        assertThat(util.lastMethod).isEqualTo(HttpMethod.GET);
    }

    @Test
    void deleteAsync_routesToDelete() {
        RecordingClient util = new RecordingClient();
        util.deleteAsync("/users/1").join();
        assertThat(util.lastMethod).isEqualTo(HttpMethod.DELETE);
    }

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    /**
     * Subclass that overrides the four core send methods to record the
     * {@code (HttpMethod, ApiRequest)} pair instead of doing any HTTP I/O.
     * The constructor passes nulls for the collaborators we don't use; only the
     * ObjectMapper is real because {@code serialize()} runs at the wrapper level.
     */
    static class RecordingClient extends RestApiClientUtil {
        HttpMethod lastMethod;
        ApiRequest lastRequest;

        RecordingClient() {
            super(null, null, new ObjectMapper());
        }

        @Override
        public ApiResponse send(HttpMethod method, ApiRequest request) {
            this.lastMethod = method;
            this.lastRequest = request;
            return ApiResponse.builder().statusCode(200).data("{}").build();
        }

        @Override
        public CompletableFuture<ApiResponse> sendAsync(HttpMethod method, ApiRequest request) {
            return CompletableFuture.completedFuture(send(method, request));
        }

        @Override
        public <T> T sendTyped(HttpMethod method, ApiRequest request, Class<T> responseType) {
            send(method, request);
            return null;
        }

        @Override
        public <T> CompletableFuture<T> sendAsyncTyped(HttpMethod method, ApiRequest request, Class<T> responseType) {
            sendTyped(method, request, responseType);
            return CompletableFuture.completedFuture(null);
        }
    }

    record TestUser(String name, String email) {}
}
