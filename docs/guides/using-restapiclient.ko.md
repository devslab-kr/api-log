# RestApiClient 사용하기

`RestApiClientUtil`은 내장 HTTP 클라이언트입니다. 로깅이 자동으로 켜진 상태로 호출하고 싶을 때 사용. Spring의 `RestClient`를 래핑하고 모든 호출에서 세 가지 라이프사이클 이벤트(`INITIATED`, `SUCCESS`, `ERROR`)를 발행합니다.

## 메서드 매트릭스

API는 의도적으로 좁게 — `GET`과 `POST` × `sync`와 `async` × `raw ApiResponse`와 `typed`. `PUT`/`DELETE`/`PATCH`는 아직 없습니다 (아래 [로드맵](#roadmap) 참고).

| 메서드 | 반환 타입 | 언제 |
|---|---|---|
| `getSync(endpoint)` | `ApiResponse` | 단순 GET, 원시 응답 래퍼가 필요할 때 |
| `getSyncTyped(endpoint, Class<T>)` | `T` | GET, DTO로 바로 역직렬화 |
| `getAsync(endpoint)` | `CompletableFuture<ApiResponse>` | 비동기 GET |
| `getAsyncTyped(endpoint, Class<T>)` | `CompletableFuture<T>` | 비동기 GET + 타입 결과 |
| `postSync(endpoint, String payload)` | `ApiResponse` | 미리 직렬화된 JSON 문자열 POST |
| `postSync(endpoint, T body)` | `ApiResponse` | DTO POST, Jackson이 직렬화 처리 |
| `postSyncTyped(endpoint, body, Class<T>)` | `T` | POST + 타입 응답 |
| `postAsync(endpoint, ...)` | `CompletableFuture<ApiResponse>` | 비동기 POST |
| `postAsyncTyped(endpoint, body, Class<T>)` | `CompletableFuture<T>` | 비동기 POST + 타입 응답 |

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

## 로드맵 {#roadmap}

아직 지원 안 함 (PR 환영):

- `PUT`, `DELETE`, `PATCH` 메서드
- 호출별 헤더 오버라이드
- 멀티파트 / 스트리밍 업로드
- WebClient (리액티브) 변형
