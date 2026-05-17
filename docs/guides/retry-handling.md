# Retry handling

External APIs fail. Networks blip. Vendors have bad afternoons. `api-log` makes every retry attempt visible — not just the final outcome — so you can spot flaky integrations before they become outages.

## Two ways retries get logged

| Source | How | `event_type` |
|---|---|---|
| Spring Retry (`@Retryable`) | Automatic — `RetryConfig` is auto-imported, retries on `ApiCallErrorEvent` produce `RETRY_ERROR` rows | `RETRY_ERROR` |
| Manual retry loop | You set `isRetry = true` and bump `retryCount` when publishing `ApiCallErrorEvent` | `RETRY_ERROR` |

## With Spring Retry

`RetryConfig` (imported by `ApiLogAutoConfiguration`) calls `@EnableRetry` for you. Annotate the method that makes the HTTP call:

```java
@Service
public class PaymentClient {

    private final RestApiClientUtil api;

    public PaymentClient(RestApiClientUtil api) {
        this.api = api;
    }

    @Retryable(
        retryFor = { ResourceAccessException.class, HttpServerErrorException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 200, multiplier = 2.0)
    )
    public ChargeResult charge(ChargeRequest req) {
        return api.postSyncTyped("/charges", req, ChargeResult.class);
    }

    @Recover
    public ChargeResult recover(Exception e, ChargeRequest req) {
        // Called after all retries exhausted. api_log already has full history.
        throw new PaymentTemporarilyUnavailableException(req.getId(), e);
    }
}
```

A call that fails twice then succeeds writes **six rows** to `api_log` for one `charge()` invocation:

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

All six share the same `request_id` so you can pull the full timeline:

```sql
SELECT event_type, retry_count, status_code, timestamp
FROM api_log
WHERE request_id = 'abc-...'
ORDER BY id;
```

## With your own retry loop

If you've already got retry logic (Resilience4j, exponential backoff library, hand-rolled loop), publish the events yourself:

```java
@Service
@RequiredArgsConstructor
public class FlakyVendorClient {

    private final ApplicationEventPublisher publisher;
    private final HttpClient http;

    public Result call(Request input) {
        ApiRequest req = ApiRequest.builder()
                .endpoint("/vendor/api")
                .payload(input.toJson())
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

## Common queries

**Top endpoints by retry rate (last 24h):**

```sql
SELECT endpoint,
       COUNT(*) FILTER (WHERE event_type = 'RETRY_ERROR') AS retries,
       COUNT(*) FILTER (WHERE event_type IN ('SUCCESS','ERROR')) AS terminals,
       ROUND(
         COUNT(*) FILTER (WHERE event_type = 'RETRY_ERROR')::numeric
           / NULLIF(COUNT(*) FILTER (WHERE event_type IN ('SUCCESS','ERROR')), 0),
         2
       ) AS retries_per_call
FROM api_log
WHERE timestamp > NOW() - INTERVAL '24 hours'
GROUP BY endpoint
HAVING COUNT(*) FILTER (WHERE event_type = 'RETRY_ERROR') > 0
ORDER BY retries_per_call DESC;
```

**Calls that needed retries to succeed:**

```sql
SELECT request_id, endpoint, MAX(retry_count) AS attempts_before_success
FROM api_log
WHERE request_id IN (
    SELECT request_id FROM api_log WHERE event_type = 'SUCCESS'
)
AND request_id IN (
    SELECT request_id FROM api_log WHERE event_type = 'RETRY_ERROR'
)
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

## See also

- [Publishing events manually](publishing-events.md) — the underlying event API
- [Querying logs](querying-logs.md) — more SQL patterns
- [Reference / Events](../reference/events.md) — full event type semantics
