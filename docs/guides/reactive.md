# Reactive (WebFlux)

If your app is already on `WebClient` / `WebFlux`, drop in `ReactiveApiClientUtil` for the same logging pipeline but with a reactor-native API. Same events, same `api_log` rows — just `Mono<ApiResponse>` instead of `ApiResponse`.

## Requirements

`spring-webflux` is **optional** in the starter — you add it yourself. Once it's on the classpath, the starter auto-registers `ReactiveApiClientUtil` and `RestApiClientUtil` side-by-side (both are usable in the same app).

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

## At a glance

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

Behind the scenes:

1. Before the request fires → `ApiCallInitiatedEvent` published → listener writes an `INITIATED` row
2. On 2xx response → `ApiCallSuccessEvent` → `SUCCESS` row with the real status code and body
3. On 4xx/5xx or network failure → `ApiCallErrorEvent` → `ERROR` row with `status_code`, structured `error_message`, and the upstream `responseBody`

## Method matrix

Same shape as the blocking client — every verb × raw / typed, plus the `send()` / `sendTyped()` cores.

| Method | Returns |
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

## Customizing the underlying `WebClient`

The starter builds `ReactiveApiClientUtil` from Spring Boot's auto-configured `WebClient.Builder`. Customize via the standard `WebClientCustomizer` pattern — the customizations flow through automatically:

```java
@Bean
public WebClientCustomizer apiBaseUrl() {
    return builder -> builder
            .baseUrl("https://api.example.com")
            .defaultHeader("Authorization", "Bearer " + System.getenv("API_KEY"));
}
```

If you need a completely separate `WebClient` for this client only (different base URL, different timeouts), provide your own `ReactiveApiClientUtil` bean:

```java
@Bean
ReactiveApiClientUtil vendorApiClient(ApplicationEventPublisher publisher, ObjectMapper mapper) {
    WebClient vendor = WebClient.builder()
            .baseUrl("https://vendor.example.com")
            .build();
    return new ReactiveApiClientUtil(vendor, publisher, mapper);
}
```

The starter's `@ConditionalOnMissingBean` backs off.

## Retry correlation

Same trick as the blocking client — build the `ApiRequest` yourself and reuse it across retry attempts:

```java
public Mono<ChargeResult> charge(ChargeRequest input) {
    ApiRequest req = ApiRequest.builder()
            .endpoint("/charges")
            .payload(mapper.writeValueAsString(input))
            .requestId(input.getChargeId())          // stable across retries
            .build();

    return api.sendTyped(HttpMethod.POST, req, ChargeResult.class)
            .retryWhen(Retry.backoff(3, Duration.ofMillis(200))
                    .filter(throwable -> throwable instanceof WebClientResponseException.ServiceUnavailable));
}
```

All attempts write rows sharing `chargeId` as `request_id` — query the full timeline with `WHERE request_id = ...`.

## Coexistence with blocking `RestApiClientUtil`

The two clients are independent beans. You can inject both in the same service and pick per-call:

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

Same `api_log` rows for both — the table doesn't care which path produced them.

## See also

- [Using RestApiClientUtil](using-restapiclient.md) — the blocking equivalent
- [Publishing events manually](publishing-events.md) — for non-HTTP work or your own client
- [Retry handling](retry-handling.md) — full retry pattern, including the listener's DB-write retries
