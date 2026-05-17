# 재시도 처리

외부 API는 실패합니다. 네트워크는 흔들립니다. 벤더에게도 안 좋은 날이 있습니다. `api-log`는 모든 재시도 시도를 볼 수 있게 해줍니다 — 다만 **어떻게** 볼 수 있는지는 재시도가 HTTP 레이어(당신의 코드)에서 일어나는지 로그 쓰기 레이어(리스너)에서 일어나는지에 따라 다릅니다.

## 두 가지 재시도 레이어, 두 가지 이야기

| 레이어 | 누가 재시도 | `api_log`에 보임? |
|---|---|---|
| **HTTP 호출** (벤더로 가는 외부 요청) | 당신의 코드 (`@Retryable`, Resilience4j, 직접 작성한 루프) | **이벤트를 직접 발행할 때만** 가능 — 아래 참고. 번들 `RestApiClientUtil`은 아직 재시도 컨텍스트를 전파하지 않음. |
| **로그 쓰기** (리스너의 `api_log` INSERT) | `ApiEventListener` 자체 — `@Retryable(maxAttempts=3, backoff=1s)` | 투명. 실패한 로그 쓰기는 자동 재시도; 세 번 다 실패하면 리스너가 포기 (앱은 계속 동작). |

이 가이드는 주로 HTTP 호출 레이어를 다룹니다.

## 주의: `RestApiClientUtil`과 HTTP 재시도

`RestApiClientUtil` 호출을 `@Retryable`로 감싸면:

```java
@Retryable(retryFor = HttpServerErrorException.class, maxAttempts = 3)
public ChargeResult charge(ChargeRequest req) {
    return api.postSyncTyped("/charges", req, ChargeResult.class);  // ← 번들 클라이언트
}
```

…재시도는 동작하지만 `api_log` 행들이 시도 간 **상관관계가 없습니다**:

- 호출마다 **새 `request_id`** (UUID) 생성됨. 시도 1의 행들과 시도 2의 행들이 서로 다른 correlation key 가짐.
- 에러 행은 모두 `retry_count = 0`, `is_retry = false` (재시도 컨텍스트가 전달되지 않음).

재시도 타임라인 가시성이 중요하다면 그 호출은 `RestApiClientUtil` 대신 **이벤트를 직접 발행**하세요 (아래).

(`RestApiClientUtil`에 재시도 컨텍스트를 통합하는 건 로드맵에 있습니다 — [Contributing](../contributing.md#roadmap) 참고.)

## HTTP 재시도 추적 — 이벤트 직접 발행

`requestId`를 당신이 소유하므로 시도 간 재사용 가능합니다. 깔끔한 재시도 타임라인을 얻는 지원되는 방법:

```java
@Service
@RequiredArgsConstructor
public class FlakyVendorClient {

    private final ApplicationEventPublisher publisher;
    private final HttpClient http;   // 자체 HTTP 클라이언트

    public Result call(Request input) {
        ApiRequest req = ApiRequest.builder()
                .endpoint("/vendor/api")
                .payload(input.toJson())
                // 모든 재시도에서 같은 requestId — 이게 correlation key.
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
                // retryCount = attempt; 1, 2번째 시도에서 isRetry = true
                publisher.publishEvent(new ApiCallErrorEvent(this, req, e, attempt, isRetry));
                if (attempt < 2) sleep(backoff(attempt));
            }
        }

        throw new RuntimeException("vendor unreachable after 3 attempts", lastError);
    }
}
```

두 번 실패하고 세 번째에 성공한 호출의 결과 — `request_id`로 상관된 **여섯 행**:

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

타임라인 조회:

```sql
SELECT event_type, retry_count, status_code, timestamp
FROM api_log
WHERE request_id = 'abc-...'
ORDER BY id;
```

## 로그 쓰기 회복력 — `ApiEventListener`가 해주는 것

PostgreSQL 연결이 INSERT 도중 흔들려도, 단 한 번의 로그 행 손실로 트리거 요청 자체가 죽지 않도록 해야 합니다. `ApiEventListener`가 처리:

```java
@EventListener
@Async
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
public void onApiCallInitiated(ApiCallInitiatedEvent event) {
    apiLogService.saveApiCallInitiated(event);
}
```

세 저장 메서드(`onApiCallInitiated`, `onApiCallSuccess`, `onApiCallError`)에 각각 `@Retryable(maxAttempts=3, backoff=1s)` 적용. 저장이 던지면 Spring Retry가 리스너를 최대 3회 1초 백오프로 재시도. 소진되면 ERROR 레벨로 로깅하고 앱은 계속 — 비즈니스 경로는 불안정한 로그 테이블에 영향받지 않음.

투명함 — 설정 필요 없음. 이게 운영에서 켜둘 수 있게 만드는 핵심.

## 자주 쓰는 쿼리

**재시도 끝에 성공한 호출:**

```sql
SELECT request_id, endpoint, MAX(retry_count) AS attempts_before_success
FROM api_log
WHERE request_id IN (SELECT request_id FROM api_log WHERE event_type = 'SUCCESS')
  AND request_id IN (SELECT request_id FROM api_log WHERE event_type = 'RETRY_ERROR')
GROUP BY request_id, endpoint;
```

**재시도 모두 소진하고 실패한 호출:**

```sql
SELECT endpoint, request_id, MAX(retry_count) AS final_attempt, MAX(timestamp) AS gave_up_at
FROM api_log
WHERE request_id NOT IN (SELECT request_id FROM api_log WHERE event_type = 'SUCCESS')
  AND event_type = 'RETRY_ERROR'
GROUP BY endpoint, request_id
ORDER BY gave_up_at DESC;
```

**엔드포인트별 재시도율 (최근 24h):**

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

## 같이 보기

- [이벤트 직접 발행](publishing-events.md) — 내부 이벤트 API
- [로그 조회](querying-logs.md) — 더 많은 SQL 패턴
- [레퍼런스 / 이벤트](../reference/events.md) — 이벤트 타입 의미론
