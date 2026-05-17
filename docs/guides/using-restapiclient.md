# Using `RestApiClientUtil`

`RestApiClientUtil` is the bundled HTTP client. Use it when you want logging on automatically; it wraps Spring's `RestClient` and fires the three lifecycle events (`INITIATED`, `SUCCESS`, `ERROR`) on every call.

## Method matrix

The API is intentionally narrow — `GET` and `POST` × `sync` and `async` × `raw ApiResponse` and `typed`. There's no `PUT`/`DELETE`/`PATCH` yet (see [Roadmap](#roadmap) below).

| Method | Returns | Use when |
|---|---|---|
| `getSync(endpoint)` | `ApiResponse` | Simple GET, you want the raw response wrapper |
| `getSyncTyped(endpoint, Class<T>)` | `T` | GET, deserialize directly into your DTO |
| `getAsync(endpoint)` | `CompletableFuture<ApiResponse>` | Fire-and-await GET |
| `getAsyncTyped(endpoint, Class<T>)` | `CompletableFuture<T>` | Async GET with typed result |
| `postSync(endpoint, String payload)` | `ApiResponse` | POST a pre-serialized JSON string |
| `postSync(endpoint, T body)` | `ApiResponse` | POST a DTO, Jackson handles serialization |
| `postSyncTyped(endpoint, body, Class<T>)` | `T` | POST + typed response |
| `postAsync(endpoint, ...)` | `CompletableFuture<ApiResponse>` | Async POST |
| `postAsyncTyped(endpoint, body, Class<T>)` | `CompletableFuture<T>` | Async POST + typed response |

## Synchronous GET

```java
@Service
public class UserService {

    private final RestApiClientUtil api;

    public UserService(RestApiClientUtil api) {
        this.api = api;
    }

    // Raw response (status code + body as String)
    public ApiResponse fetchRaw(long id) {
        return api.getSync("/users/" + id);
    }

    // Typed response — Jackson maps the body into User
    public User fetchUser(long id) {
        return api.getSyncTyped("/users/" + id, User.class);
    }
}
```

After each call, two rows appear in `api_log`: one `INITIATED`, one `SUCCESS` (or `ERROR` if the call failed).

## Synchronous POST

```java
public User createUser(CreateUserRequest input) {
    // postSyncTyped:  POST + auto-deserialize the response
    return api.postSyncTyped("/users", input, User.class);
}

// Or if you've already serialized the body:
public ApiResponse createRaw(String json) {
    return api.postSync("/users", json);
}
```

## Async variants

Async methods return `CompletableFuture` so you can compose calls without blocking the caller thread:

```java
public CompletableFuture<User> enrichUser(long id) {
    return api.getAsyncTyped("/users/" + id, User.class)
              .thenCompose(user ->
                  api.getAsyncTyped("/profiles/" + user.getId(), Profile.class)
                     .thenApply(profile -> user.withProfile(profile))
              );
}
```

Each leg of the chain still produces its own `INITIATED`/`SUCCESS` pair in `api_log`.

## Error path

When an HTTP call fails (4xx, 5xx, connection refused, timeout), `RestApiClientUtil` publishes an `ApiCallErrorEvent` and re-throws. You handle it in normal Java:

```java
try {
    User user = api.getSyncTyped("/users/1", User.class);
    // ...
} catch (RestClientException e) {
    // The ERROR row is already in api_log with the same request_id.
    // You don't need to log it again — query by request_id later for diagnostics.
    log.warn("user fetch failed", e);
}
```

The `api_log` rows for a failed call:

```text
 event_type | endpoint  | status_code | error_message
------------+-----------+-------------+----------------------------------
 ERROR      | /users/1  |         404 | {"type":"...","message":"..."}
 INITIATED  | /users/1  |             |
```

## Customizing the underlying `RestClient`

By default, `RestApiClientUtil` uses Spring Boot's auto-configured `RestClient`. To target a specific base URL or attach default headers, define your own:

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

`RestApiClientUtil` picks this up via standard Spring injection.

## When not to use this

`RestApiClientUtil` is opinionated — sync GETs return `ApiResponse`, async ones return `CompletableFuture`, no streaming, no multipart, no fine-grained header overrides per call. If you need those, **bring your own HTTP client and [publish events manually](publishing-events.md)** — the logging side works independently.

## Roadmap

Not yet supported (PRs welcome):

- `PUT`, `DELETE`, `PATCH` methods
- Per-call header override
- Multipart / streaming uploads
- WebClient (reactive) variant
