package kr.devslab.apilog.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Shared JSON conversion used by every backend writer when building rows for
 * the {@code payload} / {@code response} / {@code error_message} JSONB columns.
 *
 * <p>The two methods cover the two patterns every writer needs:
 * <ul>
 *   <li>{@link #toJsonNode(String)} — turn a string body into a {@code JsonNode}.
 *       Falls back to {@code { "raw": "..." }} when parsing fails so non-JSON
 *       payloads still land in the column intact.</li>
 *   <li>{@link #buildErrorJson(Throwable, String)} — build the structured
 *       {@code error_message} shape:
 *       <pre>{ "type": "&lt;fqcn&gt;", "message": "&lt;exception message&gt;" [, "responseBody": "..."] }</pre>
 *       The {@code responseBody} field only appears when {@link HttpErrorExtractor}
 *       found one — saves a few bytes per row for non-HTTP failures.</li>
 * </ul>
 */
public final class PayloadJsonMapper {

    private final ObjectMapper objectMapper;

    public PayloadJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode toJsonNode(String data) {
        if (data == null) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(data);
        } catch (Exception e) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("raw", data);
            return node;
        }
    }

    public JsonNode buildErrorJson(Throwable error, String responseBody) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", error.getClass().getName());
        node.put("message", error.getMessage());
        if (responseBody != null && !responseBody.isEmpty()) {
            node.put("responseBody", responseBody);
        }
        return node;
    }

    /**
     * String form of {@link #toJsonNode(String)} — JSON canonical form for
     * backends (R2DBC, MyBatis) that store the column as text rather than as
     * Jackson's {@code JsonNode}.
     */
    public String toJsonString(String data) {
        try {
            return objectMapper.writeValueAsString(toJsonNode(data));
        } catch (Exception e) {
            // Should be impossible — toJsonNode always returns a valid JsonNode
            // and ObjectMapper.writeValueAsString of one can't throw a parse error.
            // Wrap as RuntimeException so the call site doesn't need to declare.
            throw new IllegalStateException("Failed to serialize JsonNode to String", e);
        }
    }

    /**
     * Convenience for backends that store {@code error_message} as JSON text.
     */
    public String buildErrorJsonString(Throwable error, String responseBody) {
        try {
            return objectMapper.writeValueAsString(buildErrorJson(error, responseBody));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize error JsonNode to String", e);
        }
    }
}
