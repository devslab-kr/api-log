# 이벤트

네 가지 이벤트 타입이 API 호출의 라이프사이클을 표현합니다. `api_log`의 `event_type` 값과 1:1 매핑.

## 이벤트 계층

세 Java 이벤트 클래스 모두 `org.springframework.context.ApplicationEvent` 상속:

```
ApplicationEvent
   ├── ApiCallInitiatedEvent    → INITIATED   행
   ├── ApiCallSuccessEvent      → SUCCESS     행
   └── ApiCallErrorEvent        → ERROR 또는 RETRY_ERROR 행
                                   (is_retry에 따라)
```

참고: 이벤트 **클래스**는 셋이지만 `ApiCallErrorEvent`는 `isRetry` 플래그에 따라 `ERROR` 또는 `RETRY_ERROR` 행을 생성 — 어떤 `event_type`을 쓸지 `ApiLogService`가 결정합니다.

## `ApiCallInitiatedEvent`

요청 전송 직전에 발행. 호출이 영원히 매달리거나 응답 전에 JVM이 죽어도 "우리가 X를 호출하려 했다"는 기록이 남습니다.

**생성자:**

```java
new ApiCallInitiatedEvent(Object source, ApiRequest request)
```

**이벤트에서 사용 가능한 필드:**

| 필드 | 타입 | 비고 |
|---|---|---|
| `request` | `ApiRequest` | 전송할 endpoint + payload |
| `eventTimestamp` | `LocalDateTime` | 이벤트 생성 시각 |

**결과 `api_log` 행:**

| 컬럼 | 값 |
|---|---|
| `event_type` | `INITIATED` |
| `request_id` | UUID (발행자가 생성) |
| `endpoint` | `request.endpoint`에서 |
| `payload` | `request.payload`에서 (JSONB 직렬화) |
| `response`, `status_code`, `error_message` | NULL |
| `retry_count` | `0` (첫 시도) |
| `is_retry` | `false` |

## `ApiCallSuccessEvent`

성공 HTTP 응답(2xx) 후 발행. `RestApiClientUtil`이 아닌 호출자라면 "성공"의 정의는 직접 결정합니다.

**생성자:**

```java
new ApiCallSuccessEvent(Object source, ApiRequest request, ApiResponse response)
```

**필드:**

| 필드 | 타입 | 비고 |
|---|---|---|
| `request` | `ApiRequest` | 원래 요청 |
| `response` | `ApiResponse` | `data` (본문) + `statusCode` |
| `eventTimestamp` | `LocalDateTime` | |

**결과 행:**

| 컬럼 | 값 |
|---|---|
| `event_type` | `SUCCESS` |
| `response` | `response.data`에서 (JSONB) |
| `status_code` | `response.statusCode`에서 |
| `error_message` | NULL |

## `ApiCallErrorEvent`

호출 실패 시 발행. 종료 에러와 재시도 모두에 사용됨 — `isRetry` 플래그가 `event_type`을 `ERROR`와 `RETRY_ERROR` 사이에서 전환.

**생성자:**

```java
new ApiCallErrorEvent(
    Object source,
    ApiRequest request,
    Throwable error,
    int retryCount,
    boolean isRetry
)
```

**필드:**

| 필드 | 타입 | 비고 |
|---|---|---|
| `request` | `ApiRequest` | 실패한 요청 |
| `error` | `Throwable` | 예외 |
| `retryCount` | `int` | 첫 시도 `0`, 재시도 `1+` |
| `isRetry` | `boolean` | 재시도 시도 `true`, 첫 시도 `false` |
| `eventTimestamp` | `LocalDateTime` | |

**결과 행:**

| 컬럼 | 값 |
|---|---|
| `event_type` | `isRetry == true`이면 `RETRY_ERROR`, 아니면 `ERROR` |
| `error_message` | `{"type": "<FQCN>", "message": "<메시지>"}`. 예외가 Spring `HttpStatusCodeException` / `RestClientResponseException`이고 본문이 있으면 추가로 `"responseBody": "<업스트림 본문>"` 필드 포함 |
| `status_code` | `HttpStatusCodeException.getStatusCode().value()`에서 추출 (`RestClientResponseException`도 동일). 비-HTTP 예외 (타임아웃, 연결 거부 등)는 NULL |
| `retry_count` | `retryCount`에서 |
| `is_retry` | `isRetry`에서 |

## 이벤트 타입 치트시트

| `event_type` | 소스 | `response` 있음 | `error_message` 있음 | `status_code` 있음 |
|---|---|---|---|---|
| `INITIATED` | `ApiCallInitiatedEvent` | ❌ | ❌ | ❌ |
| `SUCCESS` | `ApiCallSuccessEvent` | ✅ | ❌ | ✅ |
| `ERROR` | `ApiCallErrorEvent` (`isRetry=false`) | ❌ | ✅ | 때때로 |
| `RETRY_ERROR` | `ApiCallErrorEvent` (`isRetry=true`) | ❌ | ✅ | 때때로 |

## 직접 이벤트 리스닝

`api-log`의 리스너 외에 추가 리스너를 연결할 수 있습니다 — 알람, 메트릭, 사이드 이펙트:

```java
@Component
public class HighErrorRateAlerter {

    @EventListener
    @Async
    public void onError(ApiCallErrorEvent event) {
        if (event.getError() instanceof HttpServerErrorException &&
            "/critical/endpoint".equals(event.getRequest().getEndpoint())) {
            slackClient.notify("5xx on critical endpoint: " + event.getError().getMessage());
        }
    }
}
```

같은 이벤트에 여러 리스너가 붙어도 독립적으로 실행 — `api-log`는 행을 쓰고, 당신의 알러터는 Slack을 보냅니다. 둘 다 호출자를 막지 않습니다.

## 같이 보기

- [스키마 레퍼런스](schema.md) — 컬럼 형식
- [이벤트 발행 가이드](../guides/publishing-events.md) — 자체 코드에서 이벤트 발행하는 법
- [재시도 처리 가이드](../guides/retry-handling.md) — 실전 `RETRY_ERROR`
