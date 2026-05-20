package kr.devslab.apilog.spi;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Pulls HTTP status + response body off a thrown exception so backend writers
 * can record them into {@code api_log.status_code} / {@code api_log.error_message}.
 *
 * <p>Direct {@code instanceof} works for the Spring Web (blocking) hierarchy —
 * we depend on {@code spring-web} via {@code compileOnly}, so the symbols
 * resolve when consumers have it on their classpath. For Spring WebFlux's
 * {@link RestClientResponseException} cousins ({@code WebClientResponseException}
 * and its concrete subclasses), we duck-type via reflection because
 * {@code spring-webflux} is also optional and we don't want to force its
 * classpath presence just to identify it.
 *
 * <p>Used by every backend writer ({@code JpaApiLogWriter},
 * {@code R2dbcApiLogWriter}, {@code MybatisApiLogWriter}). Kept stateless +
 * thread-safe so it can be invoked from any async/reactive context.
 */
public final class HttpErrorExtractor {

    private HttpErrorExtractor() {
        // utility class — no instances
    }

    public static HttpErrorInfo extract(Throwable error) {
        if (error instanceof HttpStatusCodeException ex) {
            return new HttpErrorInfo(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        }
        if (error instanceof RestClientResponseException ex) {
            return new HttpErrorInfo(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        }
        // Match WebClientResponseException + its concrete subclasses
        // (NotFound, BadRequest, etc.) by package prefix so unrelated
        // exceptions that happen to share method names don't get matched.
        if (error.getClass().getName()
                .startsWith("org.springframework.web.reactive.function.client.WebClientResponseException")) {
            try {
                Object status = error.getClass().getMethod("getStatusCode").invoke(error);
                Integer statusValue = (Integer) status.getClass().getMethod("value").invoke(status);
                Object body = error.getClass().getMethod("getResponseBodyAsString").invoke(error);
                return new HttpErrorInfo(statusValue, body == null ? null : body.toString());
            } catch (ReflectiveOperationException ignored) {
                // Shape didn't match — fall through to EMPTY.
            }
        }
        return HttpErrorInfo.EMPTY;
    }
}
