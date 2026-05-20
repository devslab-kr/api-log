# 리액티브 (WebFlux)

앱이 이미 `WebClient` / `WebFlux`를 쓰고 있다면 `ReactiveApiClientUtil`로 drop-in. 같은 로깅 파이프라인, 같은 `api_log` 행, 시그니처만 `Mono<ApiResponse>`.

## 요구사항

`spring-webflux`는 스타터에서 **옵셔널** — 직접 추가하세요. 클래스패스에 있으면 스타터가 `ReactiveApiClientUtil`과 `RestApiClientUtil`을 나란히 자동 등록 (한 앱에서 둘 다 사용 가능).

=== "Maven"

    ```xml
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>io.projectreactor.netty</groupId>
        <artifactId>reactor-netty-http</artifactId>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    implementation("org.springframework:spring-webflux")
    implementation("io.projectreactor.netty:reactor-netty-http")
    ```

## 한눈에 보기

```java
@Service
public class UserService {

    private final ReactiveApiClientUtil api;

    public UserService(ReactiveApiClientUtil api) {
        this.api = api;
    }

    public Mono<User> fetchUser(long id) {
        return api.getTyped("/users/" + id, User.class);
    }
}
```

뒤에서 일어나는 일:

1. 요청 발사 전 → `ApiCallInitiatedEvent` 발행 → 리스너가 `INITIATED` 행 기록
2. 2xx 응답 시 → `ApiCallSuccessEvent` → 실제 상태 코드와 본문을 가진 `SUCCESS` 행
3. 4xx/5xx 또는 네트워크 실패 시 → `ApiCallErrorEvent` → `status_code`, 구조화된 `error_message`, 업스트림 `responseBody`를 가진 `ERROR` 행

## 메서드 매트릭스

블로킹 클라이언트와 동일한 형태 — 모든 verb × raw / typed + `send()` / `sendTyped()` 코어.

| 메서드 | 반환 |
|---|---|
| `get(endpoint)` | `Mono<ApiResponse>` |
| `getTyped(endpoint, Class<T>)` | `Mono<T>` |
| `post(endpoint, String payload)` | `Mono<ApiResponse>` |
| `post(endpoint, T body)` | `Mono<ApiResponse>` |
| `postTyped(endpoint, body, Class<T>)` | `Mono<T>` |
| `put(endpoint, ...)` / `putTyped(...)` | `Mono<ApiResponse>` / `Mono<T>` |
| `delete(endpoint)` / `deleteTyped(endpoint, Class<T>)` | `Mono<ApiResponse>` / `Mono<T>` |
| `patch(endpoint, ...)` / `patchTyped(...)` | `Mono<ApiResponse>` / `Mono<T>` |
| `send(HttpMethod, ApiRequest)` | `Mono<ApiResponse>` |
| `sendTyped(HttpMethod, ApiRequest, Class<T>)` | `Mono<T>` |

## 내부 `WebClient` 커스터마이징

스타터는 Spring Boot가 자동 구성한 `WebClient.Builder`에서 `ReactiveApiClientUtil`을 빌드합니다. 표준 `WebClientCustomizer` 패턴으로 커스터마이즈 — 자동으로 흘러갑니다:

```java
@Bean
public WebClientCustomizer apiBaseUrl() {
    return builder -> builder
            .baseUrl("https://api.example.com")
            .defaultHeader("Authorization", "Bearer " + System.getenv("API_KEY"));
}
```

이 클라이언트 전용으로 완전히 별개의 `WebClient`가 필요하면 (다른 base URL, 다른 타임아웃 등) 직접 `ReactiveApiClientUtil` 빈을 제공:

```java
@Bean
ReactiveApiClientUtil vendorApiClient(ApplicationEventPublisher publisher, ObjectMapper mapper) {
    WebClient vendor = WebClient.builder()
            .baseUrl("https://vendor.example.com")
            .build();
    return new ReactiveApiClientUtil(vendor, publisher, mapper);
}
```

스타터의 `@ConditionalOnMissingBean`이 양보합니다.

## 재시도 상관관계

블로킹 클라이언트와 같은 패턴 — `ApiRequest`를 직접 만들어 재시도 시도 간 재사용:

```java
public Mono<ChargeResult> charge(ChargeRequest input) {
    ApiRequest req = ApiRequest.builder()
            .endpoint("/charges")
            .payload(mapper.writeValueAsString(input))
            .requestId(input.getChargeId())          // 재시도 전반에서 안정적
            .build();

    return api.sendTyped(HttpMethod.POST, req, ChargeResult.class)
            .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                    .filter(throwable -> throwable instanceof WebClientResponseException.ServiceUnavailable));
}
```

모든 시도가 `chargeId`를 `request_id`로 공유 — `WHERE request_id = ...`로 전체 타임라인 조회.

## 블로킹 `RestApiClientUtil`과 공존

두 클라이언트는 독립 빈. 같은 서비스에서 둘 다 주입하고 호출별로 선택 가능:

```java
@Service
@RequiredArgsConstructor
public class MixedService {

    private final RestApiClientUtil blockingApi;
    private final ReactiveApiClientUtil reactiveApi;

    public User loadSync(long id) {
        return blockingApi.getSyncTyped("/users/" + id, User.class);
    }

    public Mono<User> loadReactive(long id) {
        return reactiveApi.getTyped("/users/" + id, User.class);
    }
}
```

둘 다 동일한 `api_log` 행 — 테이블은 어느 경로로 만들어졌는지 신경 쓰지 않음.

## 같이 보기

- [RestApiClientUtil 사용하기](using-restapiclient.md) — 블로킹 버전
- [이벤트 직접 발행](publishing-events.md) — 비-HTTP 작업 또는 자체 클라이언트
- [재시도 처리](retry-handling.md) — 전체 재시도 패턴 + 리스너의 DB 쓰기 재시도
