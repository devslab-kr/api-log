package kr.devslab.apilog;

/**
 * Shared string constants for the {@code event_type} column written to
 * {@code api_log}. Kept as plain string constants (rather than an enum) so
 * downstream queries — e.g., {@code WHERE event_type = 'SUCCESS'} from a BI
 * tool — match what the application writes.
 */
public final class Constants {

    public static final String INITIATED = "INITIATED";
    public static final String SUCCESS = "SUCCESS";
    public static final String RETRY_ERROR = "RETRY_ERROR";
    public static final String ERROR = "ERROR";

    private Constants() {
        // utility class — no instances
    }
}
