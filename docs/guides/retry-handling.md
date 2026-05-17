# Retry handling

External APIs fail. Networks blip. Vendors have bad afternoons. `api-log` can make every retry attempt visible — but the **how** depends on whether the retry happens at the HTTP layer (your code) or at the log-write layer (the listener).

## Two retry layers, two stories

| Layer | Who retries | Visible in `api_log`? |
|---|---|---|
| **HTTP call** (the outbound request to a vendor) | Your code (`@Retryable`, Resilience4j, hand-rolled) | Only if **you publish events manually** — see below. The bundled `RestApiClientUtil` does NOT propagate retry context yet. |
| **Log write** (the listener's INSERT into `api_log`) | `ApiEventListener` itself — `@Retryable(maxAttempts=3, backoff=1s)` | Transparent. Failed log writes are retried automatically; if all three attempts fail, the listener gives up (your app keeps running). |

The rest of this guide covers the HTTP-call layer.

## Heads-up: `RestApiClientUtil` and HTTP retries

If you wrap a `RestApiClientUtil` call with `@Retryable`:

```java
@Retryable(retryFor = HttpServerErrorException.class, maxAttempts = 3)
public ChargeResult charge(ChargeRequest req) {
    return api.postSyncTyped("/charges", req, ChargeResult.class);  // ← bundled client
}
```

…you'll get retries that work, but the `api_log` rows are NOT correlated across attempts:

- Each call generates a **fresh `request_id`** (UUID), so attempt 1's rows and attempt 2's rows have different correlation keys.
- The error rows all read `retry_count = 0`, `is_retry = false` (no retry context is plumbed through).

If retry-timeline visibility matters, **publish events manually** instead of using `RestApiClientUtil` for that call. See below.

(Plumbing retry context into `RestApiClientUtil` is on the roadmap; see [Contributing](../contributing.md#roadmap).)

## Tracking HTTP retries — publish events manually

You own the `requestId`, so you can reuse it across attempts. This is the supported way to get a clean retry timeline:

```java
@Service
@RequiredArgsConstructor
public class FlakyVendorClient {

    private final ApplicationEventPublisher publisher;
    private final HttpClient http;   // your own HTTP client

    public Result call(Request input) {
        ApiRequest req = ApiRequest.builder()
                .endpoint("/vendor/api")
                .payload(input.toJson())
                // Same requestId across all retries — that's the correlation key.
                .requestId(UUID.randomUUID().toString())
                .build();

        Exception lastError = null;

        for (int attempt = 0; attempt < 3; attempt++) {
            boolean isRetry = attempt > 0;
            publisher.publishEvent(new ApiCallInitiatedEvent(this, req));

            try {
                Result result = doHttpCall(req);
                publisher.publishEvent(new ApiCallSuccessEvent(this, req,
                    ApiResponse.builder().data(result.toJson()).statusCode(200).build()));
                return result;
            } catch (Exception e) {
                lastError = e;
                // retryCount = attempt; isRetry = true on attempts 1, 2
                publisher.publishEvent(new ApiCallErrorEvent(this, req, e, attempt, isRetry));
                if (attempt < 2) sleep(backoff(attempt));
            }
        }

        throw new RuntimeException("vendor unreachable after 3 attempts", lastError);
    }
}
```

A call that fails twice then succeeds writes **six rows** correlated by `request_id`:

```text
 id | event_type   | request_id  | retry_count | is_retry | status_code
----+--------------+-------------+-------------+----------+-------------
  6 | SUCCESS      | abc-...     |           0 | false    |         200
  5 | INITIATED    | abc-...     |           0 | false    |
  4 | RETRY_ERROR  | abc-...     |           1 | true     |         503
  3 | INITIATED    | abc-...     |           1 | true     |
  2 | RETRY_ERROR  | abc-...     |           0 | false    |         503
  1 | INITIATED    | abc-...     |           0 | false    |
```

Pull the timeline:

```sql
SELECT event_type, retry_count, status_code, timestamp
FROM api_log
WHERE request_id = 'abc-...'
ORDER BY id;
```

## Log-write resilience — what `ApiEventListener` does for you

If your PostgreSQL connection wobbles mid-INSERT, you don't want a single dropped log row to take down the request that triggered it. `ApiEventListener` handles this:

```java
@EventListener
@Async
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
public void onApiCallInitiated(ApiCallInitiatedEvent event) {
    apiLogService.saveApiCallInitiated(event);
}
```

The three save methods (`onApiCallInitiated`, `onApiCallSuccess`, `onApiCallError`) each have `@Retryable(maxAttempts=3, backoff=1s)`. If a save throws, Spring Retry retries the listener up to 3 times with 1-second backoff. After exhaustion, the failure is logged at ERROR level and the application continues — your business path is never affected by a flaky log table.

This is transparent — you don't configure anything. It's what makes the starter safe to leave on in production.

## Common queries

**Calls that needed retries to succeed:**

```sql
SELECT request_id, endpoint, MAX(retry_count) AS attempts_before_success
FROM api_log
WHERE request_id IN (SELECT request_id FROM api_log WHERE event_type = 'SUCCESS')
  AND request_id IN (SELECT request_id FROM api_log WHERE event_type = 'RETRY_ERROR')
GROUP BY request_id, endpoint;
```

**Calls that exhausted all retries and still failed:**

```sql
SELECT endpoint, request_id, MAX(retry_count) AS final_attempt, MAX(timestamp) AS gave_up_at
FROM api_log
WHERE request_id NOT IN (SELECT request_id FROM api_log WHERE event_type = 'SUCCESS')
  AND event_type = 'RETRY_ERROR'
GROUP BY endpoint, request_id
ORDER BY gave_up_at DESC;
```

**Retry rate per endpoint (last 24h):**

```sql
SELECT endpoint,
       COUNT(*) FILTER (WHERE event_type = 'RETRY_ERROR') AS retries,
       COUNT(*) FILTER (WHERE event_type IN ('SUCCESS','ERROR')) AS terminals
FROM api_log
WHERE timestamp > NOW() - INTERVAL '24 hours'
GROUP BY endpoint
HAVING COUNT(*) FILTER (WHERE event_type = 'RETRY_ERROR') > 0
ORDER BY retries DESC;
```

## See also

- [Publishing events manually](publishing-events.md) — the underlying event API
- [Querying logs](querying-logs.md) — more SQL patterns
- [Reference / Events](../reference/events.md) — full event type semantics
