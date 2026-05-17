# Publishing events manually

If you already have an HTTP client (WebClient, OkHttp, Apache HttpClient, a vendor SDK), you don't need `RestApiClientUtil`. Just publish the three lifecycle events yourself — the rest of the pipeline (listener → service → repository → PostgreSQL) works the same way.

## The three events

| Event | Fired when | Constructor |
|---|---|---|
| `ApiCallInitiatedEvent` | Before sending the request | `(source, ApiRequest)` |
| `ApiCallSuccessEvent` | Request returned a 2xx response | `(source, ApiRequest, ApiResponse)` |
| `ApiCallErrorEvent` | Request failed (4xx, 5xx, network, timeout) | `(source, ApiRequest, Throwable, retryCount, isRetry)` |

All three extend `org.springframework.context.ApplicationEvent`. `ApiEventListener` is `@Async`, so publishing is non-blocking.

## Minimal example

```java
@Service
@RequiredArgsConstructor
public class WebhookSender {

    private final ApplicationEventPublisher publisher;
    private final OkHttpClient http;   // your own HTTP client

    public void send(String url, String payload) {
        ApiRequest request = ApiRequest.builder()
                .endpoint(url)
                .payload(payload)
                .build();

        // INITIATED — fired immediately, regardless of outcome
        publisher.publishEvent(new ApiCallInitiatedEvent(this, request));

        try {
            Response httpResp = http.newCall(
                new Request.Builder().url(url).post(RequestBody.create(payload, JSON)).build()
            ).execute();

            ApiResponse response = ApiResponse.builder()
                    .data(httpResp.body() != null ? httpResp.body().string() : null)
                    .statusCode(httpResp.code())
                    .build();

            publisher.publishEvent(new ApiCallSuccessEvent(this, request, response));
        } catch (IOException e) {
            publisher.publishEvent(new ApiCallErrorEvent(this, request, e, 0, false));
            throw new RuntimeException("webhook delivery failed", e);
        }
    }
}
```

After one successful call, `api_log` contains two rows: `INITIATED` and `SUCCESS`. After a failed call: `INITIATED` and `ERROR`.

## The `ApiRequest` DTO

```java
ApiRequest request = ApiRequest.builder()
        .endpoint("/some/path")    // goes to the endpoint column
        .payload(jsonString)       // goes to the payload JSONB column
        .build();
```

The `payload` field is stored as JSONB. If you pass a plain Java object instead of a JSON string, Jackson serializes it for you via the `ObjectMapper` bean (`ApiLogService` handles the conversion).

## The `ApiResponse` DTO

```java
ApiResponse response = ApiResponse.builder()
        .data(jsonString)          // goes to the response JSONB column
        .statusCode(200)           // goes to the status_code column
        .build();
```

## Mixing `RestApiClientUtil` and manual publishing

You can use both in the same codebase. `RestApiClientUtil` publishes events for HTTP calls it makes; manual `publishEvent` calls add rows for clients it doesn't know about. They land in the same `api_log` table, queryable together.

## When to use this vs `RestApiClientUtil`

- **`RestApiClientUtil`** — simplest path; you don't already have an HTTP client and don't need streaming/multipart/per-call headers
- **Manual publishing** — you have an existing client (WebClient, OkHttp, vendor SDK), you need fine-grained header control, you log calls that aren't HTTP at all (gRPC, message queue dispatch, etc.)

## Tracking retries

If you implement retry logic yourself, pass `retryCount` and `isRetry` to `ApiCallErrorEvent`:

```java
for (int attempt = 0; attempt < 3; attempt++) {
    try {
        return doHttpCall(request);
    } catch (IOException e) {
        boolean isRetry = attempt > 0;
        publisher.publishEvent(new ApiCallErrorEvent(this, request, e, attempt, isRetry));
        if (attempt == 2) throw e;
    }
}
```

The `event_type` for retry attempts becomes `RETRY_ERROR` (handled by `ApiLogService`). See [Retry handling](retry-handling.md) for the full picture.

## Logging non-HTTP calls

The starter isn't strictly HTTP-only. Anything you publish events for ends up in `api_log` — message queue dispatches, gRPC calls, internal RPC. Just set `endpoint` to something meaningful (e.g., `kafka://orders.created`, `grpc:UserService/GetUser`) and the JSONB columns store whatever you put there.
