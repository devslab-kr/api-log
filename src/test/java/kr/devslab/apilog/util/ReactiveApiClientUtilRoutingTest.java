package kr.devslab.apilog.util;

import kr.devslab.apilog.model.dto.ApiRequest;
import kr.devslab.apilog.model.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link ReactiveApiClientUtil} convenience methods funnel to the
 * core {@code send()} / {@code sendTyped()} with the correct {@link HttpMethod}
 * and a well-built {@link ApiRequest}.
 *
 * <p>No real WebClient, no Spring context — we subclass and intercept the core
 * methods to record arguments, just like {@link RestApiClientUtilRoutingTest}.
 */
class ReactiveApiClientUtilRoutingTest {

    @Test
    void get_routesToGetWithoutPayload() {
        RecordingReactiveClient util = new RecordingReactiveClient();
        util.get("/users/1").block();
        assertThat(util.lastMethod).isEqualTo(HttpMethod.GET);
        assertThat(util.lastRequest.getEndpoint()).isEqualTo("/users/1");
        assertThat(util.lastRequest.getPayload()).isNull();
    }

    @Test
    void getTyped_routesToGet_andDeserializes() {
        RecordingReactiveClient util = new RecordingReactiveClient();
        util.stubReturn("{\"name\":\"Ada\"}");
        TestUser user = util.getTyped("/users/1", TestUser.class).block();
        assertThat(util.lastMethod).isEqualTo(HttpMethod.GET);
        assertThat(user).isNotNull();
        assertThat(user.name()).isEqualTo("Ada");
    }

    @Test
    void post_routesToPostWithStringPayload() {
        RecordingReactiveClient util = new RecordingReactiveClient();
        util.post("/users", "{\"name\":\"Ada\"}").block();
        assertThat(util.lastMethod).isEqualTo(HttpMethod.POST);
        assertThat(util.lastRequest.getPayload()).isEqualTo("{\"name\":\"Ada\"}");
    }

    @Test
    void post_withObject_serializesViaJackson() {
        RecordingReactiveClient util = new RecordingReactiveClient();
        util.post("/users", new TestUser("Ada", "ada@example.com")).block();
        assertThat(util.lastMethod).isEqualTo(HttpMethod.POST);
        assertThat(util.lastRequest.getPayload()).contains("\"name\":\"Ada\"");
        assertThat(util.lastRequest.getPayload()).contains("\"email\":\"ada@example.com\"");
    }

    @Test
    void put_routesToPut() {
        RecordingReactiveClient util = new RecordingReactiveClient();
        util.put("/users/1", "{}").block();
        assertThat(util.lastMethod).isEqualTo(HttpMethod.PUT);
    }

    @Test
    void delete_routesToDelete_withoutPayload() {
        RecordingReactiveClient util = new RecordingReactiveClient();
        util.delete("/users/1").block();
        assertThat(util.lastMethod).isEqualTo(HttpMethod.DELETE);
        assertThat(util.lastRequest.getPayload()).isNull();
    }

    @Test
    void patch_routesToPatch() {
        RecordingReactiveClient util = new RecordingReactiveClient();
        util.patch("/users/1", "{\"name\":\"new\"}").block();
        assertThat(util.lastMethod).isEqualTo(HttpMethod.PATCH);
    }

    @Test
    void sendCore_respectsCallerProvidedRequestId() {
        RecordingReactiveClient util = new RecordingReactiveClient();
        ApiRequest req = ApiRequest.builder()
                .endpoint("/x")
                .payload("body")
                .requestId("caller-id-abc")
                .build();

        util.send(HttpMethod.POST, req).block();

        assertThat(util.lastRequest.getRequestId()).isEqualTo("caller-id-abc");
    }

    // -------- Test doubles --------

    record TestUser(String name, String email) {}

    /**
     * Captures {@code send()} arguments so the verb routing can be asserted
     * without a real WebClient.
     */
    static class RecordingReactiveClient extends ReactiveApiClientUtil {
        HttpMethod lastMethod;
        ApiRequest lastRequest;
        private String stub = "{}";

        RecordingReactiveClient() {
            super(null, event -> { /* no-op publisher */ }, new ObjectMapper());
        }

        void stubReturn(String json) {
            this.stub = json;
        }

        @Override
        public Mono<ApiResponse> send(HttpMethod method, ApiRequest request) {
            this.lastMethod = method;
            this.lastRequest = request;
            return Mono.just(ApiResponse.builder()
                    .data(stub)
                    .statusCode(200)
                    .build());
        }
    }
}
