package kr.devslab.apilog.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kr.devslab.apilog.dto.ApiRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

/**
 * Wire-level integration test for {@link RestApiClientUtil}. Drives the real
 * client against an in-process MockWebServer so every byte that hits the
 * socket can be asserted on — Content-Type header, raw body, HTTP method,
 * absence of internal-only fields, charset, the lot.
 *
 * <p>Catches the v3.0.1 regression class directly: before the fix the
 * {@code postSync*} / {@code putSync*} / {@code patchSync*} helpers sent
 * {@code Content-Type: text/plain;charset=ISO-8859-1} because Spring's
 * {@code StringHttpMessageConverter} is what {@code RestClient.body(String)}
 * routes through when no explicit content type is given. Downstream services
 * deserialising with {@code @RequestBody Foo} then rejected the call as
 * Unsupported Media Type. The existing {@code RestApiClientUtilRoutingTest}
 * didn't catch this because it never actually hit a socket.
 *
 * <p>Why MockWebServer and not Testcontainers: the bug is entirely client-side
 * (how Spring formats an outbound HTTP request). A real OS-level network round
 * trip adds latency and a Docker dependency without testing anything different
 * — the bytes that leave the JVM are the same either way.
 */
class RestApiClientUtilWireIT {

    private MockWebServer server;
    private RestApiClientUtil util;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RestClient restClient = RestClient.builder().build();
        util = new RestApiClientUtil(restClient, publisher, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String url(String path) {
        return server.url(path).toString();
    }

    /** Stock 200 + JSON body response; most tests don't care about the response. */
    private void enqueueOkJson() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));
    }

    // =========================================================================
    // Primary regression coverage — body-carrying verbs must declare application/json.
    // =========================================================================

    @Test
    void postSyncTyped_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.postSyncTyped(url("/widgets"), new TestPojo("Ada", 42), TestPojo.class);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void postSync_rawJsonString_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.postSync(url("/widgets"), "{\"name\":\"Ada\"}");

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void putSyncTyped_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.putSyncTyped(url("/widgets/1"), new TestPojo("Ada", 42), TestPojo.class);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void putSync_rawString_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.putSync(url("/widgets/1"), "{\"name\":\"Ada\"}");

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void patchSyncTyped_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.patchSyncTyped(url("/widgets/1"), new TestPojo("Ada", 42), TestPojo.class);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
    }

    @Test
    void patchSync_rawString_sendsApplicationJsonContentType() throws InterruptedException {
        enqueueOkJson();
        util.patchSync(url("/widgets/1"), "{\"name\":\"Ada\"}");

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
        util.send(HttpMethod.POST, req);

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getHeader("Content-Type")).startsWith("application/json");
    }

    // =========================================================================
    // Body-less verbs / null payload must NOT declare a Content-Type.
    // (Spring's RestClient omits the header when no body is sent.)
    // =========================================================================

    @Test
    void getSync_sendsNoContentTypeHeader_noBody() throws InterruptedException {
        enqueueOkJson();
        util.getSync(url("/widgets/1"));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).isNull();
        assertThat(req.getBody().size()).isZero();
    }

    @Test
    void deleteSync_sendsNoContentTypeHeader_noBody() throws InterruptedException {
        enqueueOkJson();
        util.deleteSync(url("/widgets/1"));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).isNull();
        assertThat(req.getBody().size()).isZero();
    }

    @Test
    void send_postWithNullPayload_sendsNoContentTypeHeader() throws InterruptedException {
        enqueueOkJson();
        ApiRequest req = ApiRequest.builder().endpoint(url("/widgets")).build();
        util.send(HttpMethod.POST, req);

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        // The util's exchange() takes the no-body branch when payload is null,
        // so RestClient sends a POST with no Content-Type. This matches the
        // existing routing-test contract — null payload means "no body".
        assertThat(recorded.getHeader("Content-Type")).isNull();
        assertThat(recorded.getBody().size()).isZero();
    }

    // =========================================================================
    // Body bytes — exact serialised JSON, UTF-8 encoded.
    // =========================================================================

    @Test
    void postSyncTyped_bodyBytesAreSerializedJsonOfPojo() throws InterruptedException {
        enqueueOkJson();
        util.postSyncTyped(url("/widgets"), new TestPojo("Ada", 42), TestPojo.class);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        String body = req.getBody().readString(StandardCharsets.UTF_8);
        assertThat(body).isEqualTo("{\"name\":\"Ada\",\"score\":42}");
    }

    @Test
    void postSync_rawJsonString_bodyBytesArePassedThroughVerbatim() throws InterruptedException {
        enqueueOkJson();
        String payload = "{\"manuallyCrafted\":\"json\",\"nested\":{\"k\":\"v\"}}";
        util.postSync(url("/widgets"), payload);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getBody().readString(StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void postSyncTyped_unicodeBody_isUtf8Encoded() throws InterruptedException {
        enqueueOkJson();
        // Korean + emoji forces a non-ASCII byte sequence. Caught here = caught at
        // any future regression where charset gets pinned to ISO-8859-1 again.
        util.postSyncTyped(url("/widgets"), new TestPojo("한글-Ada-🎉", 42), TestPojo.class);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        String body = req.getBody().readString(StandardCharsets.UTF_8);
        assertThat(body).contains("한글-Ada-🎉");
    }

    @Test
    void postSyncTyped_largeBody_isSentIntact() throws InterruptedException {
        enqueueOkJson();
        // 32 KB single string — well above any reasonable buffer-flush boundary but
        // still small enough to not need chunked transfer in tests. Catches any
        // body-truncation regression at the message-converter layer.
        String big = "x".repeat(32 * 1024);
        util.postSync(url("/widgets"), "{\"big\":\"" + big + "\"}");

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        String body = req.getBody().readString(StandardCharsets.UTF_8);
        assertThat(body).hasSize("{\"big\":\"\"}".length() + big.length());
        assertThat(body).contains(big);
    }

    // =========================================================================
    // HTTP method — what the caller asked for is what hits the wire.
    // =========================================================================

    @Test
    void httpMethod_GET_arrivesAsGet() throws InterruptedException {
        enqueueOkJson();
        util.getSync(url("/x"));
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getMethod()).isEqualTo("GET");
    }

    @Test
    void httpMethod_POST_arrivesAsPost() throws InterruptedException {
        enqueueOkJson();
        util.postSync(url("/x"), "{}");
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getMethod()).isEqualTo("POST");
    }

    @Test
    void httpMethod_PUT_arrivesAsPut() throws InterruptedException {
        enqueueOkJson();
        util.putSync(url("/x"), "{}");
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getMethod()).isEqualTo("PUT");
    }

    @Test
    void httpMethod_DELETE_arrivesAsDelete() throws InterruptedException {
        enqueueOkJson();
        util.deleteSync(url("/x"));
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getMethod()).isEqualTo("DELETE");
    }

    @Test
    void httpMethod_PATCH_arrivesAsPatch() throws InterruptedException {
        enqueueOkJson();
        util.patchSync(url("/x"), "{}");
        assertThat(server.takeRequest(2, TimeUnit.SECONDS).getMethod()).isEqualTo("PATCH");
    }

    // =========================================================================
    // Internal-only fields must not leak to the wire.
    // =========================================================================

    @Test
    void send_customRequestId_doesNotLeakIdIntoWireHeaders() throws InterruptedException {
        enqueueOkJson();
        ApiRequest req = ApiRequest.builder()
                .endpoint(url("/widgets"))
                .payload("{}")
                .requestId("internal-correlation-only-92a8f1")
                .build();
        util.send(HttpMethod.POST, req);

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        // requestId is for in-JVM event correlation only — it must NOT become an
        // HTTP header or leak into the body. (Documented contract; verified here.)
        for (String headerName : recorded.getHeaders().names()) {
            assertThat(recorded.getHeader(headerName))
                    .as("header %s must not carry requestId", headerName)
                    .doesNotContain("internal-correlation-only-92a8f1");
        }
        assertThat(recorded.getBody().readString(StandardCharsets.UTF_8))
                .doesNotContain("internal-correlation-only-92a8f1");
    }

    // =========================================================================
    // Async path — same wire contract, just via CompletableFuture.
    // =========================================================================

    @Test
    void postAsync_eventuallySendsApplicationJsonContentType()
            throws InterruptedException, ExecutionException, TimeoutException {
        enqueueOkJson();
        util.postAsync(url("/widgets"), "{}").get(2, TimeUnit.SECONDS).getStatusCode();

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
        assertThat(req.getMethod()).isEqualTo("POST");
    }

    @Test
    void sendAsync_postWithBody_eventuallySendsApplicationJsonContentType()
            throws InterruptedException, ExecutionException, TimeoutException {
        enqueueOkJson();
        ApiRequest req = ApiRequest.builder().endpoint(url("/x")).payload("{\"k\":\"v\"}").build();
        util.sendAsync(HttpMethod.POST, req).get(2, TimeUnit.SECONDS);

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getHeader("Content-Type")).startsWith("application/json");
    }

    // =========================================================================
    // Test fixtures
    // =========================================================================

    /**
     * Tiny POJO with field order chosen so the natural Jackson serialisation
     * is stable across runs ({@code {"name":"...","score":N}}). Used by the
     * tests that assert exact body bytes.
     */
    public record TestPojo(String name, int score) {}

    // Stop the IDE / compiler from warning on the unused checked-exception type
    // for tests that only declare it for ExecutionException's transitive throws.
    @SuppressWarnings("unused")
    private static final Class<?> KEEP_EXECUTION_EXCEPTION_REACHABLE = ExecutionException.class;
}
