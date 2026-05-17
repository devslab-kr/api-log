# 재시도 처리

외부 API는 실패합니다. 네트워크는 흔들립니다. 벤더에게도 안 좋은 날이 있습니다. `api-log`는 최종 결과뿐 아니라 모든 재시도 시도를 볼 수 있게 해서 — 불안정한 연동을 장애가 되기 전에 발견할 수 있게 합니다.

## 재시도가 기록되는 두 가지 경로

| 출처 | 방법 | `event_type` |
|---|---|---|
| Spring Retry (`@Retryable`) | 자동 — `RetryConfig`가 자동 임포트되어, `ApiCallErrorEvent` 재시도가 `RETRY_ERROR` 행으로 기록됨 | `RETRY_ERROR` |
| 수동 재시도 루프 | `ApiCallErrorEvent` 발행 시 `isRetry = true`, `retryCount` 증가 | `RETRY_ERROR` |

## Spring Retry로

`RetryConfig`(`ApiLogAutoConfiguration`이 임포트)가 `@EnableRetry`를 호출합니다. HTTP 호출하는 메서드에 어노테이션:

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
        // 모든 재시도 소진 후 호출. api_log에 전체 이력이 이미 있음.
        throw new PaymentTemporarilyUnavailableException(req.getId(), e);
    }
}
```

두 번 실패하고 세 번째에 성공한 `charge()` 한 번의 호출은 `api_log`에 **여섯 행**:

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

여섯 행 모두 같은 `request_id`를 가지므로 전체 타임라인을 한 번에 조회 가능:

```sql
SELECT event_type, retry_count, status_code, timestamp
FROM api_log
WHERE request_id = 'abc-...'
ORDER BY id;
```

## 자체 재시도 루프로

이미 재시도 로직(Resilience4j, 백오프 라이브러리, 직접 작성한 루프)이 있다면 이벤트를 직접 발행:

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
                // retryCount = attempt; 1, 2번째 시도에서 isRetry = true
                publisher.publishEvent(new ApiCallErrorEvent(this, req, e, attempt, isRetry));
                if (attempt < 2) sleep(backoff(attempt));
            }
        }

        throw new RuntimeException("vendor unreachable after 3 attempts", lastError);
    }
}
```

## 자주 쓰는 쿼리

**재시도율 높은 엔드포인트 (최근 24시간):**

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

**재시도 끝에 성공한 호출:**

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

**재시도 모두 소진하고도 실패한 호출:**

```sql
SELECT endpoint, request_id, MAX(retry_count) AS final_attempt, MAX(timestamp) AS gave_up_at
FROM api_log
WHERE request_id NOT IN (SELECT request_id FROM api_log WHERE event_type = 'SUCCESS')
  AND event_type = 'RETRY_ERROR'
GROUP BY endpoint, request_id
ORDER BY gave_up_at DESC;
```

## 같이 보기

- [이벤트 직접 발행](publishing-events.md) — 내부 이벤트 API
- [로그 조회](querying-logs.md) — 더 많은 SQL 패턴
- [레퍼런스 / 이벤트](../reference/events.md) — 이벤트 타입 의미론
