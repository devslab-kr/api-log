# RestApiClient 사용하기

`RestApiClientUtil`은 내장 HTTP 클라이언트입니다. 로깅이 자동으로 켜진 상태로 호출하고 싶을 때 사용. Spring의 `RestClient`를 래핑하고 모든 호출에서 세 가지 라이프사이클 이벤트(`INITIATED`, `SUCCESS`, `ERROR`)를 발행합니다.

## 메서드 매트릭스

다섯 HTTP 동사(`GET`, `POST`, `PUT`, `DELETE`, `PATCH`) × `sync` / `async` × raw `ApiResponse` / 타입 결과. 그리고 고급용 코어 `send()` API (재시도 correlation, 커스텀 HTTP 메서드).

### 편의 메서드 (일반적 케이스)

| 메서드 | 반환 타입 | 언제 |
|---|---|---|
| `getSync(endpoint)` | `ApiResponse` | 단순 GET, 원시 응답 |
| `getSyncTyped(endpoint, Class<T>)` | `T` | GET + Jackson 역직렬화 |
| `getAsync(endpoint)` | `CompletableFuture<ApiResponse>` | 비동기 GET |
| `getAsyncTyped(endpoint, Class<T>)` | `CompletableFuture<T>` | 비동기 GET + 타입 |
| `postSync(endpoint, String payload)` | `ApiResponse` | 미리 직렬화된 JSON POST |
| `postSync(endpoint, T body)` | `ApiResponse` | DTO POST (Jackson 직렬화) |
| `postSyncTyped(endpoint, body, Class<T>)` | `T` | POST + 타입 응답 |
| `postAsync(...)` | `CompletableFuture<ApiResponse>` | 비동기 POST |
| `postAsyncTyped(...)` | `CompletableFuture<T>` | 비동기 POST + 타입 |
| `putSync(endpoint, String payload)` | `ApiResponse` | 미리 직렬화된 JSON PUT |
| `putSync(endpoint, T body)` | `ApiResponse` | DTO PUT |
| `putSyncTyped(endpoint, body, Class<T>)` | `T` | PUT + 타입 응답 |
| `putAsync(...)` | `CompletableFuture<ApiResponse>` | 비동기 PUT |
| `putAsyncTyped(...)` | `CompletableFuture<T>` | 비동기 PUT + 타입 |
| `deleteSync(endpoint)` | `ApiResponse` | DELETE (본문 없음) |
| `deleteSyncTyped(endpoint, Class<T>)` | `T` | DELETE + 타입 응답 |
| `deleteAsync(endpoint)` | `CompletableFuture<ApiResponse>` | 비동기 DELETE |
| `deleteAsyncTyped(endpoint, Class<T>)` | `CompletableFuture<T>` | 비동기 DELETE + 타입 |
| `patchSync(endpoint, String/T payload)` | `ApiResponse` | PATCH |
| `patchSyncTyped(endpoint, body, Class<T>)` | `T` | PATCH + 타입 응답 |
| `patchAsync(...)` / `patchAsyncTyped(...)` | future 변형 | 비동기 PATCH |

### 코어 API (고급)

| 메서드 | 반환 타입 | 언제 |
|---|---|---|
| `send(HttpMethod, ApiRequest)` | `ApiResponse` | 호출자가 모든 것 제어 — `requestId` 포함. 재시도 간 correlation에 사용. |
| `sendTyped(HttpMethod, ApiRequest, Class<T>)` | `T` | 타입 응답 |
| `sendAsync(HttpMethod, ApiRequest)` | `CompletableFuture<ApiResponse>` | 비동기 |
| `sendAsyncTyped(HttpMethod, ApiRequest, Class<T>)` | `CompletableFuture<T>` | 비동기 + 타입 |

편의 래퍼는 모두 코어 메서드로 흐릅니다.

## 동기 GET

```java
@Service
public class UserService {

    private final RestApiClientUtil api;

    public UserService(RestApiClientUtil api) {
        this.api = api;
    }

    // 원시 응답 (상태 코드 + String 본문)
    public ApiResponse fetchRaw(long id) {
        return api.getSync("/users/" + id);
    }

    // 타입 응답 — Jackson이 본문을 User로 매핑
    public User fetchUser(long id) {
        return api.getSyncTyped("/users/" + id, User.class);
    }
}
```

각 호출 뒤 `api_log`에 두 행이 생성됩니다: `INITIATED` 하나, `SUCCESS` 하나 (실패 시 `ERROR`).

## 동기 POST

```java
public User createUser(CreateUserRequest input) {
    // postSyncTyped:  POST + 응답 자동 역직렬화
    return api.postSyncTyped("/users", input, User.class);
}

// 본문을 이미 직렬화한 경우:
public ApiResponse createRaw(String json) {
    return api.postSync("/users", json);
}
```

## 비동기 변형

비동기 메서드는 `CompletableFuture`를 리턴해 호출자 스레드를 막지 않고 호출을 조합할 수 있습니다:

```java
public CompletableFuture<User> enrichUser(long id) {
    return api.getAsyncTyped("/users/" + id, User.class)
              .thenCompose(user ->
                  api.getAsyncTyped("/profiles/" + user.getId(), Profile.class)
                     .thenApply(profile -> user.withProfile(profile))
              );
}
```

체인의 각 호출이 각자 `INITIATED`/`SUCCESS` 쌍을 `api_log`에 기록합니다.

## 에러 경로

HTTP 호출이 실패하면 (4xx, 5xx, 연결 거부, 타임아웃) `RestApiClientUtil`은 `ApiCallErrorEvent`를 발행하고 예외를 다시 던집니다. 일반 Java로 처리:

```java
try {
    User user = api.getSyncTyped("/users/1", User.class);
    // ...
} catch (RestClientException e) {
    // ERROR 행은 이미 같은 request_id로 api_log에 들어가 있습니다.
    // 다시 로깅할 필요 없음 — 진단은 나중에 request_id로 조회하면 됩니다.
    log.warn("user fetch failed", e);
}
```

실패한 호출의 `api_log` 행:

```text
 event_type | endpoint  | status_code | error_message
------------+-----------+-------------+----------------------------------
 ERROR      | /users/1  |         404 | {"type":"...","message":"..."}
 INITIATED  | /users/1  |             |
```

## 내부 `RestClient` 커스터마이징

기본적으로 `RestApiClientUtil`은 Spring Boot 자동 구성된 `RestClient`를 사용합니다. 특정 base URL이나 기본 헤더를 추가하려면 직접 정의:

```java
@Configuration
public class ApiClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
            .baseUrl("https://api.example.com")
            .defaultHeader("Authorization", "Bearer " + System.getenv("API_KEY"))
            .build();
    }
}
```

`RestApiClientUtil`은 표준 Spring 주입으로 이를 가져옵니다.

## 사용하지 말아야 할 때

`RestApiClientUtil`은 의도적으로 단순합니다 — 동기 GET은 `ApiResponse`, 비동기는 `CompletableFuture`, 스트리밍 없음, 멀티파트 없음, 호출별 헤더 오버라이드 없음. 이게 필요하면 **자체 HTTP 클라이언트를 쓰고 [이벤트만 직접 발행](publishing-events.md)** 하면 됩니다 — 로깅 쪽은 독립적으로 동작합니다.

## 재시도 간 `request_id` 공유

`@Retryable`(또는 자체 재시도 루프)로 감싼 호출은 기본 API에서 매 시도마다 새 UUID를 생성 — 재시도 시도들이 correlation 없이 `api_log`에 흩어집니다. 같은 키를 공유하게 하려면 `ApiRequest`를 직접 빌드해서 `send()`에 전달:

```java
@Retryable(retryFor = HttpServerErrorException.class, maxAttempts = 3)
public ChargeResult charge(ChargeRequest input) {
    ApiRequest req = ApiRequest.builder()
            .endpoint("/charges")
            .payload(objectMapper.writeValueAsString(input))
            .requestId(input.getChargeId())      // 재시도 간 동일
            .build();
    return api.sendTyped(HttpMethod.POST, req, ChargeResult.class);
}
```

이제 그 재시도 체인의 모든 `api_log` 행이 `request_id`를 공유하므로 `WHERE request_id = '<chargeId>'`로 타임라인 조회 가능.

전체 패턴 (수동 이벤트 발행 포함)은 [재시도 처리](retry-handling.md) 참고.

## PUT / DELETE / PATCH

POST/GET과 같은 형태. 빠른 예시:

```java
// PUT — 리소스 교체
User updated = api.putSyncTyped("/users/1", user, User.class);

// DELETE — 보통 본문 없음, JSON 응답 가능
ApiResponse r = api.deleteSync("/users/1");
DeletionReceipt dr = api.deleteSyncTyped("/users/1", DeletionReceipt.class);

// PATCH — 부분 업데이트
User patched = api.patchSyncTyped("/users/1", Map.of("email", "new@example.com"), User.class);
```

전부 비동기 변형 있음 (`putAsync`, `deleteAsyncTyped` 등).

## 로드맵 {#roadmap}

아직 지원 안 함 (PR 환영):

- 호출별 헤더 오버라이드
- 멀티파트 / 스트리밍 업로드
- WebClient (리액티브) 변형
- 자동 재시도 컨텍스트 전파 (현재는 호출자가 `ApiRequest`로 `requestId` 직접 전달)
