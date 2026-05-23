package kr.devslab.apilog.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import kr.devslab.apilog.dto.ApiRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wire-level integration test for {@link ReactiveApiClientUtil}. Mirror image
 * of {@link RestApiClientUtilWireIT} but for the reactive client.
 *
 * <p>Catches the same v3.0.1 Content-Type regression on the WebClient code
 * path: before the fix, {@code spec.bodyValue(stringJson)} routed through the
 * default String encoder and went out as {@code text/plain}. Same downstream
 * symptom (Unsupported Media Type at {@code @RequestBody Foo}), same fix
 * shape ({@code spec.contentType(APPLICATION_JSON).bodyValue(...)}).
 *
 * <p>The existing {@link ReactiveApiClientUtilRoutingTest} doesn't catch this
 * because it intercepts the convenience methods before they reach
 * {@code exchange()}.
 */
class ReactiveApiClientUtilWireIT {

    private MockWebServer server;
    private ReactiveApiClientUtil util;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ObjectMapper objectMapper = new ObjectMapper();
        WebClient webClient = WebClient.builder().build();
        util = new ReactiveApiClientUtil(webClient, publisher, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String url(String path) {
        return server.url(path).toString();
    }

    private void enqueueOkJson() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));
    }

    // =========================================================================
    // Body-carrying verbs declare application/json (primary regression coverage).
    // =========================================================================

    @Test
    void postTyped_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.postTyped(url("/widgets"), new TestPojo("Ada", 42), TestPojo.class)
                .block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void post_rawJsonString_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.post(url("/widgets"), "{\"name\":\"Ada\"}").block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void putTyped_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.putTyped(url("/widgets/1"), new TestPojo("Ada", 42), TestPojo.class)
                .block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void put_rawString_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.put(url("/widgets/1"), "{\"name\":\"Ada\"}").block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void patchTyped_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.patchTyped(url("/widgets/1"), new TestPojo("Ada", 42), TestPojo.class)
                .block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void patch_rawString_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.patch(url("/widgets/1"), "{\"name\":\"Ada\"}").block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void send_postWithExplicitApiRequestBody_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        ApiRequest req = ApiRequest.builder()
                .endpoint(url("/widgets"))
                .payload("{\"raw\":\"json\"}")
                .build();
        util.send(HttpMethod.POST, req).block(Duration.ofSeconds(2));

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getHeader("Content-Type")).startsWith("application/json");
    }

    // =========================================================================
    // Body-less verbs / null payload do NOT set Content-Type.
    // =========================================================================

    @Test
    void get_sendsNoContentTypeHeader_noBody() throws InterruptedException {
        enqueueOkJson();
        util.get(url("/widgets/1")).block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).isNull();
        assertThat(req.getBody().size()).isZero();
    }

    @Test
    void delete_sendsNoContentTypeHeader_noBody() throws InterruptedException {
        enqueueOkJson();
        util.delete(url("/widgets/1")).block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).isNull();
        assertThat(req.getBody().size()).isZero();
    }

    @Test
    void send_postWithNullPayload_sendsNoContentTypeHeader() throws InterruptedException {
        enqueueOkJson();
        ApiRequest req = ApiRequest.builder().endpoint(url("/widgets")).build();
        util.send(HttpMethod.POST, req).block(Duration.ofSeconds(2));

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getHeader("Content-Type")).isNull();
        assertThat(recorded.getBody().size()).isZero();
    }

    // =========================================================================
    // Body bytes — exact serialised JSON, UTF-8.
    // =========================================================================

    @Test
    void postTyped_bodyBytesAreSerializedJsonOfPojo() throws InterruptedException {
        enqueueOkJson();
        util.postTyped(url("/widgets"), new TestPojo("Ada", 42), TestPojo.class)
                .block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        String body = req.getBody().readString(StandardCharsets.UTF_8);
        assertThat(body).isEqualTo("{\"name\":\"Ada\",\"score\":42}");
    }

    @Test
    void post_rawJsonString_bodyBytesArePassedThroughVerbatim() throws InterruptedException {
        enqueueOkJson();
        String payload = "{\"manuallyCrafted\":\"json\",\"nested\":{\"k\":\"v\"}}";
        util.post(url("/widgets"), payload).block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getBody().readString(StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void postTyped_unicodeBody_isUtf8Encoded() throws InterruptedException {
        enqueueOkJson();
        util.postTyped(url("/widgets"), new TestPojo("한글-Ada-🎉", 42), TestPojo.class)
                .block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        String body = req.getBody().readString(StandardCharsets.UTF_8);
        assertThat(body).contains("한글-Ada-🎉");
    }

    @Test
    void postTyped_largeBody_isSentIntact() throws InterruptedException {
        enqueueOkJson();
        String big = "x".repeat(32 * 1024);
        util.post(url("/widgets"), "{\"big\":\"" + big + "\"}").block(Duration.ofSeconds(2));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        String body = req.getBody().readString(StandardCharsets.UTF_8);
        assertThat(body).hasSize("{\"big\":\"\"}".length() + big.length());
        assertThat(body).contains(big);
    }

    // =========================================================================
    // HTTP method correctness.
    // =========================================================================

    @Test
    void httpMethod_GET_arrivesAsGet() throws InterruptedException {
        enqueueOkJson();
        util.get(url("/x")).block(Duration.ofSeconds(2));
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getMethod()).isEqualTo("GET");
    }

    @Test
    void httpMethod_POST_arrivesAsPost() throws InterruptedException {
        enqueueOkJson();
        util.post(url("/x"), "{}").block(Duration.ofSeconds(2));
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getMethod()).isEqualTo("POST");
    }

    @Test
    void httpMethod_PUT_arrivesAsPut() throws InterruptedException {
        enqueueOkJson();
        util.put(url("/x"), "{}").block(Duration.ofSeconds(2));
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getMethod()).isEqualTo("PUT");
    }

    @Test
    void httpMethod_DELETE_arrivesAsDelete() throws InterruptedException {
        enqueueOkJson();
        util.delete(url("/x")).block(Duration.ofSeconds(2));
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getMethod()).isEqualTo("DELETE");
    }

    @Test
    void httpMethod_PATCH_arrivesAsPatch() throws InterruptedException {
        enqueueOkJson();
        util.patch(url("/x"), "{}").block(Duration.ofSeconds(2));
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getMethod()).isEqualTo("PATCH");
    }

    // =========================================================================
    // Internal-only fields stay internal.
    // =========================================================================

    @Test
    void send_customRequestId_doesNotLeakIntoWireHeaders() throws InterruptedException {
        enqueueOkJson();
        ApiRequest req = ApiRequest.builder()
                .endpoint(url("/widgets"))
                .payload("{}")
                .requestId("reactive-correlation-only-be17c2")
                .build();
        util.send(HttpMethod.POST, req).block(Duration.ofSeconds(2));

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        for (String headerName : recorded.getHeaders().names()) {
            assertThat(recorded.getHeader(headerName))
                    .as("header %s must not carry requestId", headerName)
                    .doesNotContain("reactive-correlation-only-be17c2");
        }
        assertThat(recorded.getBody().readString(StandardCharsets.UTF_8))
                .doesNotContain("reactive-correlation-only-be17c2");
    }

    // =========================================================================
    // Test fixtures
    // =========================================================================

    public record TestPojo(String name, int score) {}
}
