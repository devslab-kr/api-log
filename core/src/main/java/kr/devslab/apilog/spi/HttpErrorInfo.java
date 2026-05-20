package kr.devslab.apilog.spi;

/**
 * HTTP error metadata pulled off a thrown exception by {@link HttpErrorExtractor}.
 * Both fields may be {@code null} when the exception isn't an HTTP error
 * carrier (e.g., a timeout or network error before the response landed).
 */
public record HttpErrorInfo(Integer statusCode, String responseBody) {

    /** Sentinel for the no-HTTP-context case — avoids spraying null checks at call sites. */
    public static final HttpErrorInfo EMPTY = new HttpErrorInfo(null, null);
}
