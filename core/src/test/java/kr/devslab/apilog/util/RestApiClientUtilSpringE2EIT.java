package kr.devslab.apilog.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import kr.devslab.apilog.event.ApiCallErrorEvent;
import kr.devslab.apilog.event.ApiCallInitiatedEvent;
import kr.devslab.apilog.event.ApiCallSuccessEvent;
import kr.devslab.apilog.spi.ApiLogWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;

/**
 * End-to-end integration test for {@link RestApiClientUtil}: real Spring Boot
 * web app with real {@code @RequestBody}-annotated controllers, real Tomcat,
 * real Jackson. This is the test that would have caught the v3.0.1
 * Content-Type bug immediately — without {@code application/json} on the
 * outbound request, Spring's {@code @RequestBody Widget} returns 415 and the
 * whole roundtrip throws.
 *
 * <p>Complements {@link RestApiClientUtilWireIT} (precise byte-level
 * assertions via MockWebServer) with the higher-level contract: a real
 * consumer using {@code @RequestBody} can successfully receive what we send.
 * Both layers needed — the wire IT pins the exact protocol contract; this
 * one proves the actual user-facing scenario works.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Force servlet — the test classpath has both -web and -webflux, and we want
        // the servlet stack here (Tomcat + @RequestBody Foo on a POJO controller).
        properties = "spring.main.web-application-type=servlet")
@Import(RestApiClientUtilSpringE2EIT.EchoController.class)
class RestApiClientUtilSpringE2EIT {

    @LocalServerPort
    int port;

    @Autowired
    RestApiClientUtil util;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // =========================================================================
    // Round-trip across every body-carrying verb. Each test exercises both
    // sides: client sends → @RequestBody deserialises → controller echoes →
    // client deserialises. The 415 / 500 that the v3.0.1 bug produced would
    // make each of these throw.
    // =========================================================================

    @Test
    void postSyncTyped_roundtripsPojoThroughRequestBody() {
        Echo input = new Echo("Ada Lovelace", 42, null);
        Echo result = util.postSyncTyped(url("/echo"), input, Echo.class);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Ada Lovelace");
        assertThat(result.score()).isEqualTo(42);
    }

    @Test
    void putSyncTyped_roundtripsPojoThroughRequestBody() {
        Echo input = new Echo("Updated Ada", 99, null);
        Echo result = util.putSyncTyped(url("/echo/1"), input, Echo.class);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Updated Ada");
        assertThat(result.score()).isEqualTo(99);
    }

    @Test
    void patchSyncTyped_roundtripsPojoThroughRequestBody() {
        Echo input = new Echo("Patched Ada", 17, null);
        Echo result = util.patchSyncTyped(url("/echo/1"), input, Echo.class);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Patched Ada");
        assertThat(result.score()).isEqualTo(17);
    }

    @Test
    void getSyncTyped_returnsBodyFromGetEndpoint() {
        Echo result = util.getSyncTyped(url("/echo/Ada"), Echo.class);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Ada");
        assertThat(result.score()).isEqualTo(1);
    }

    @Test
    void deleteSync_returns204Status() {
        var resp = util.deleteSync(url("/echo/1"));
        assertThat(resp.getStatusCode()).isEqualTo(204);
    }

    // =========================================================================
    // Body-content correctness. These would have caught any encoding,
    // serialisation, or content-type drift independently of the routing tests.
    // =========================================================================

    @Test
    void postSyncTyped_unicodeName_roundtripsCorrectly() {
        Echo input = new Echo("한글 이름 + 🎉", 42, null);
        Echo result = util.postSyncTyped(url("/echo"), input, Echo.class);

        assertThat(result.name()).isEqualTo("한글 이름 + 🎉");
    }

    @Test
    void postSyncTyped_nullField_roundtripsCorrectly() {
        // Default Jackson serialises nulls; @RequestBody happily reads them.
        // Verifies no implicit "drop nulls" behaviour was introduced.
        Echo input = new Echo("Ada", 0, null);
        Echo result = util.postSyncTyped(url("/echo"), input, Echo.class);

        assertThat(result.name()).isEqualTo("Ada");
        assertThat(result.tags()).isNull();
    }

    @Test
    void postSyncTyped_nestedObjectAndArray_roundtripsCorrectly() {
        Echo input = new Echo("Ada", 42, new String[] {"math", "computing", "lace"});
        Echo result = util.postSyncTyped(url("/echo"), input, Echo.class);

        assertThat(result.name()).isEqualTo("Ada");
        assertThat(result.tags()).containsExactly("math", "computing", "lace");
    }

    @Test
    void postSync_rawJsonString_roundtripsThroughEchoController() {
        // Exercises the postSync(String, String) overload that bypasses the
        // typed serialisation path — caller has hand-built JSON. This must
        // still hit @RequestBody correctly (i.e., Content-Type still right).
        String raw = "{\"name\":\"raw-Ada\",\"score\":7,\"tags\":[\"a\",\"b\"]}";
        var resp = util.postSync(url("/echo"), raw);

        assertThat(resp.getStatusCode()).isEqualTo(200);
        assertThat(resp.getData()).contains("raw-Ada").contains("\"score\":7");
    }

    // =========================================================================
    // Error paths — the util surfaces server errors as Spring's typed
    // HTTP exceptions (not swallowed, not mangled).
    // =========================================================================

    @Test
    void fivexx_throwsHttpServerErrorException() {
        assertThatThrownBy(() -> util.getSyncTyped(url("/fail/5xx"), Echo.class))
                .isInstanceOf(HttpServerErrorException.class)
                .satisfies(e -> assertThat(((HttpServerErrorException) e).getStatusCode().value()).isEqualTo(500));
    }

    @Test
    void fourxx_throwsHttpClientErrorException() {
        assertThatThrownBy(() -> util.getSyncTyped(url("/fail/4xx"), Echo.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(((HttpClientErrorException) e).getStatusCode().value()).isEqualTo(418));
    }

    // =========================================================================
    // Async path — CompletableFuture variant produces same successful echo.
    // =========================================================================

    @Test
    void postAsyncTyped_completableFutureCompletesWithEcho()
            throws ExecutionException, InterruptedException,
            java.util.concurrent.TimeoutException {
        Echo input = new Echo("Async Ada", 21, null);
        Echo result = util.postAsyncTyped(url("/echo"), input, Echo.class)
                .get(5, TimeUnit.SECONDS);

        assertThat(result.name()).isEqualTo("Async Ada");
        assertThat(result.score()).isEqualTo(21);
    }

    // =========================================================================
    // Test fixtures
    // =========================================================================

    /**
     * Compact echo payload with one of each: string, primitive, optional array.
     * Field order chosen so Jackson's natural serialisation is stable enough
     * to assert on substring matches.
     */
    public record Echo(String name, int score, String[] tags) {}

    /**
     * Registered into the test context via {@code @Import}. Echoes any body it
     * receives, deliberately {@code @RequestBody} to force application/json
     * content-type negotiation — the heart of what the v3.0.1 bug broke.
     *
     * <p>Also provides a no-op {@link ApiLogWriter} bean. The core's
     * {@code ApiEventListener} needs one to satisfy its constructor injection,
     * but the actual writer implementations live in the backend modules
     * ({@code :jpa}, {@code :r2dbc}, {@code :mybatis}) which aren't on the
     * core test classpath. The no-op fills that gap so the context starts —
     * we're testing the HTTP client here, not the persistence side.
     */
    @TestConfiguration
    @RestController
    @RequestMapping("/")
    public static class EchoController {

        @Bean
        ApiLogWriter noopWriter() {
            return new ApiLogWriter() {
                @Override public void writeInitiated(ApiCallInitiatedEvent event) { /* no-op */ }
                @Override public void writeSuccess(ApiCallSuccessEvent event) { /* no-op */ }
                @Override public void writeError(ApiCallErrorEvent event) { /* no-op */ }
            };
        }


        @PostMapping("/echo")
        public Echo post(@RequestBody Echo body) {
            return body;
        }

        @PutMapping("/echo/{id}")
        public Echo put(@PathVariable Long id, @RequestBody Echo body) {
            return body;
        }

        @PatchMapping("/echo/{id}")
        public Echo patch(@PathVariable Long id, @RequestBody Echo body) {
            return body;
        }

        @GetMapping("/echo/{name}")
        public Echo get(@PathVariable String name) {
            return new Echo(name, 1, null);
        }

        @DeleteMapping("/echo/{id}")
        public ResponseEntity<Void> delete(@PathVariable Long id) {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/fail/5xx")
        public Echo fail5xx() {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "simulated 500");
        }

        @GetMapping("/fail/4xx")
        public Echo fail4xx() {
            // I'm a teapot — distinct from 400/404 so the test is unambiguous about
            // which 4xx came back.
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, "simulated 418");
        }
    }
}
