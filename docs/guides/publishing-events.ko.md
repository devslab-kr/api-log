# 이벤트 직접 발행

이미 HTTP 클라이언트(WebClient, OkHttp, Apache HttpClient, 벤더 SDK)가 있다면 `RestApiClientUtil`이 필요 없습니다. 세 가지 라이프사이클 이벤트를 직접 발행하면 — 나머지 파이프라인(리스너 → 서비스 → 리포지토리 → PostgreSQL)은 동일하게 동작합니다.

## 세 가지 이벤트

| 이벤트 | 언제 발행 | 생성자 |
|---|---|---|
| `ApiCallInitiatedEvent` | 요청 전송 직전 | `(source, ApiRequest)` |
| `ApiCallSuccessEvent` | 2xx 응답 수신 | `(source, ApiRequest, ApiResponse)` |
| `ApiCallErrorEvent` | 요청 실패 (4xx, 5xx, 네트워크, 타임아웃) | `(source, ApiRequest, Throwable, retryCount, isRetry)` |

세 클래스 모두 `org.springframework.context.ApplicationEvent`를 상속. `ApiEventListener`는 `@Async`라서 발행은 논블로킹.

## 최소 예시

```java
@Service
@RequiredArgsConstructor
public class WebhookSender {

    private final ApplicationEventPublisher publisher;
    private final OkHttpClient http;   // 자체 HTTP 클라이언트

    public void send(String url, String payload) {
        ApiRequest request = ApiRequest.builder()
                .endpoint(url)
                .payload(payload)
                .build();

        // INITIATED — 결과와 무관하게 즉시 발행
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

성공 호출 한 번에 `api_log`에 `INITIATED`와 `SUCCESS` 두 행. 실패 시 `INITIATED`와 `ERROR`.

## `ApiRequest` DTO

```java
ApiRequest request = ApiRequest.builder()
        .endpoint("/some/path")    // endpoint 컬럼으로
        .payload(jsonString)       // payload JSONB 컬럼으로
        .build();
```

`payload` 필드는 JSONB로 저장됩니다. JSON 문자열 대신 일반 Java 객체를 넣어도 Jackson이 `ObjectMapper` 빈으로 직렬화합니다 (`ApiLogService`가 변환 처리).

## `ApiResponse` DTO

```java
ApiResponse response = ApiResponse.builder()
        .data(jsonString)          // response JSONB 컬럼으로
        .statusCode(200)           // status_code 컬럼으로
        .build();
```

## `RestApiClientUtil`과 수동 발행 혼용

한 코드베이스에서 둘 다 써도 됩니다. `RestApiClientUtil`은 자신이 보낸 호출에 대해 이벤트를 발행하고, 수동 `publishEvent`는 그 외의 호출에 대해 행을 추가합니다. 같은 `api_log` 테이블에 들어가 함께 조회 가능.

## `RestApiClientUtil` vs 수동 발행

- **`RestApiClientUtil`** — 가장 간단한 경로. HTTP 클라이언트가 따로 없고 스트리밍/멀티파트/호출별 헤더가 필요 없는 경우
- **수동 발행** — 기존 클라이언트(WebClient, OkHttp, 벤더 SDK)가 있거나, 호출별 세밀한 헤더 제어가 필요하거나, HTTP가 아닌 호출까지 기록할 때 (gRPC, 메시지 큐, etc.)

## 재시도 추적

직접 재시도 로직을 구현한다면 `retryCount`와 `isRetry`를 `ApiCallErrorEvent`에 전달:

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

재시도 시도의 `event_type`은 `RETRY_ERROR`가 됩니다 (`ApiLogService`에서 처리). 자세한 내용은 [재시도 처리](retry-handling.md) 참고.

## HTTP가 아닌 호출 로깅

스타터는 HTTP 전용이 아닙니다. 이벤트만 발행하면 어떤 호출이든 `api_log`에 들어갑니다 — 메시지 큐 전송, gRPC 호출, 내부 RPC. `endpoint`에 의미 있는 값(예: `kafka://orders.created`, `grpc:UserService/GetUser`)을 넣고 JSONB 컬럼에 원하는 내용을 저장하세요.
