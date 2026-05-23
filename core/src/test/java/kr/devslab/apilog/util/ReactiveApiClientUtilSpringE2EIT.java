package kr.devslab.apilog.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * End-to-end integration test for {@link ReactiveApiClientUtil}: real Spring
 * Boot WebFlux app, real {@code @RequestBody}-annotated reactive controllers,
 * real reactor-netty. Reactive twin of {@link RestApiClientUtilSpringE2EIT}.
 *
 * <p>Boots in WebFlux mode by depending on {@code spring-boot-starter-webflux}
 * as a test dep and relying on the absence of any servlet starter from the
 * test classpath — Spring Boot picks the reactive web environment.
 *
 * <p>Catches the v3.0.1 Content-Type regression on the WebClient path: a real
 * reactive {@code @RequestBody Echo} consumer would have rejected the call
 * with 415 before the fix, and this test would have surfaced it as a 415
 * round-trip failure.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Force reactive — the test classpath has both -web and -webflux on it.
        // Spring Boot's default would pick servlet (which the sibling
        // RestApiClientUtilSpringE2EIT uses on purpose); this test needs the
        // WebFlux stack so reactor-netty handles the round-trip end to end.
        properties = "spring.main.web-application-type=reactive")
@Import(ReactiveApiClientUtilSpringE2EIT.ReactiveEchoController.class)
class ReactiveApiClientUtilSpringE2EIT {

    @LocalServerPort
    int port;

    @Autowired
    ReactiveApiClientUtil util;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // =========================================================================
    // Round-trip via @RequestBody / WebFlux reactive controllers.
    // =========================================================================

    @Test
    void postTyped_roundtripsPojoThroughReactiveRequestBody() {
        Echo input = new Echo("Ada Lovelace", 42, null);
        Echo result = util.postTyped(url("/echo"), input, Echo.class)
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Ada Lovelace");
        assertThat(result.score()).isEqualTo(42);
    }

    @Test
    void putTyped_roundtripsPojoThroughReactiveRequestBody() {
        Echo input = new Echo("Updated Ada", 99, null);
        Echo result = util.putTyped(url("/echo/1"), input, Echo.class)
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Updated Ada");
        assertThat(result.score()).isEqualTo(99);
    }

    @Test
    void patchTyped_roundtripsPojoThroughReactiveRequestBody() {
        Echo input = new Echo("Patched Ada", 17, null);
        Echo result = util.patchTyped(url("/echo/1"), input, Echo.class)
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Patched Ada");
    }

    @Test
    void getTyped_returnsBodyFromGetEndpoint() {
        Echo result = util.getTyped(url("/echo/Ada"), Echo.class).block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Ada");
        assertThat(result.score()).isEqualTo(1);
    }

    @Test
    void delete_returns204Status() {
        var resp = util.delete(url("/echo/1")).block(Duration.ofSeconds(5));
        assertThat(resp).isNotNull();
        assertThat(resp.getStatusCode()).isEqualTo(204);
    }

    // =========================================================================
    // Body-content correctness across the reactive serialisation path.
    // =========================================================================

    @Test
    void postTyped_unicodeName_roundtripsCorrectly() {
        Echo input = new Echo("한글 이름 + 🎉", 42, null);
        Echo result = util.postTyped(url("/echo"), input, Echo.class)
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("한글 이름 + 🎉");
    }

    @Test
    void postTyped_nullField_roundtripsCorrectly() {
        Echo input = new Echo("Ada", 0, null);
        Echo result = util.postTyped(url("/echo"), input, Echo.class)
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.tags()).isNull();
    }

    @Test
    void postTyped_nestedArrayField_roundtripsCorrectly() {
        Echo input = new Echo("Ada", 42, new String[] {"math", "lace"});
        Echo result = util.postTyped(url("/echo"), input, Echo.class)
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.tags()).containsExactly("math", "lace");
    }

    @Test
    void post_rawJsonString_roundtripsThroughEchoController() {
        String raw = "{\"name\":\"raw-Ada\",\"score\":7,\"tags\":[\"a\",\"b\"]}";
        var resp = util.post(url("/echo"), raw).block(Duration.ofSeconds(5));

        assertThat(resp).isNotNull();
        assertThat(resp.getStatusCode()).isEqualTo(200);
        assertThat(resp.getData()).contains("raw-Ada").contains("\"score\":7");
    }

    // =========================================================================
    // Error paths — WebClient surfaces 4xx/5xx as WebClientResponseException
    // subclasses. The util doesn't intercept; we verify it passes them through.
    // =========================================================================

    @Test
    void fivexx_propagatesAsWebClientResponseException() {
        assertThatThrownBy(() ->
                util.getTyped(url("/fail/5xx"), Echo.class).block(Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(WebClientResponseException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(500));
    }

    @Test
    void fourxx_propagatesAsWebClientResponseException() {
        assertThatThrownBy(() ->
                util.getTyped(url("/fail/4xx"), Echo.class).block(Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(WebClientResponseException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(418));
    }

    // =========================================================================
    // Test fixtures
    // =========================================================================

    public record Echo(String name, int score, String[] tags) {}

    /**
     * Reactive controller — methods return {@code Mono} to keep the entire
     * round-trip on reactor threads. {@code @RequestBody Echo} drives the
     * same content-type negotiation as the servlet variant, so the v3.0.1
     * fix is exercised here too.
     *
     * <p>Also provides a no-op {@link ApiLogWriter} for the same reason as
     * the servlet sibling — the core's listener wires against the SPI but
     * the implementations live in the backend modules.
     */
    @TestConfiguration
    @RestController
    @RequestMapping("/")
    public static class ReactiveEchoController {

        @Bean
        ApiLogWriter noopWriter() {
            return new ApiLogWriter() {
                @Override public void writeInitiated(ApiCallInitiatedEvent event) { /* no-op */ }
                @Override public void writeSuccess(ApiCallSuccessEvent event) { /* no-op */ }
                @Override public void writeError(ApiCallErrorEvent event) { /* no-op */ }
            };
        }


        @PostMapping("/echo")
        public Mono<Echo> post(@RequestBody Echo body) {
            return Mono.just(body);
        }

        @PutMapping("/echo/{id}")
        public Mono<Echo> put(@PathVariable Long id, @RequestBody Echo body) {
            return Mono.just(body);
        }

        @PatchMapping("/echo/{id}")
        public Mono<Echo> patch(@PathVariable Long id, @RequestBody Echo body) {
            return Mono.just(body);
        }

        @GetMapping("/echo/{name}")
        public Mono<Echo> get(@PathVariable String name) {
            return Mono.just(new Echo(name, 1, null));
        }

        @DeleteMapping("/echo/{id}")
        public Mono<ResponseEntity<Void>> delete(@PathVariable Long id) {
            return Mono.just(ResponseEntity.noContent().build());
        }

        @GetMapping("/fail/5xx")
        public Mono<Echo> fail5xx() {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "simulated 500"));
        }

        @GetMapping("/fail/4xx")
        public Mono<Echo> fail4xx() {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.I_AM_A_TEAPOT, "simulated 418"));
        }
    }
}
