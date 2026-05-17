# Using `RestApiClientUtil`

`RestApiClientUtil` is the bundled HTTP client. Use it when you want logging on automatically; it wraps Spring's `RestClient` and fires the three lifecycle events (`INITIATED`, `SUCCESS`, `ERROR`) on every call.

## Method matrix

All five HTTP verbs (`GET`, `POST`, `PUT`, `DELETE`, `PATCH`) × `sync` / `async` × raw `ApiResponse` / typed result. Plus a core `send()` API for advanced cases (retry correlation, custom HTTP method).

### Convenience methods (the common case)

| Method | Returns | Use when |
|---|---|---|
| `getSync(endpoint)` | `ApiResponse` | Simple GET, raw response wrapper |
| `getSyncTyped(endpoint, Class<T>)` | `T` | GET + Jackson deserialization |
| `getAsync(endpoint)` | `CompletableFuture<ApiResponse>` | Async GET |
| `getAsyncTyped(endpoint, Class<T>)` | `CompletableFuture<T>` | Async GET + typed |
| `postSync(endpoint, String payload)` | `ApiResponse` | POST pre-serialized JSON |
| `postSync(endpoint, T body)` | `ApiResponse` | POST a DTO (Jackson serializes) |
| `postSyncTyped(endpoint, body, Class<T>)` | `T` | POST + typed response |
| `postAsync(...)` | `CompletableFuture<ApiResponse>` | Async POST |
| `postAsyncTyped(...)` | `CompletableFuture<T>` | Async POST + typed |
| `putSync(endpoint, String payload)` | `ApiResponse` | PUT pre-serialized JSON |
| `putSync(endpoint, T body)` | `ApiResponse` | PUT a DTO |
| `putSyncTyped(endpoint, body, Class<T>)` | `T` | PUT + typed response |
| `putAsync(...)` | `CompletableFuture<ApiResponse>` | Async PUT |
| `putAsyncTyped(...)` | `CompletableFuture<T>` | Async PUT + typed |
| `deleteSync(endpoint)` | `ApiResponse` | DELETE (no body) |
| `deleteSyncTyped(endpoint, Class<T>)` | `T` | DELETE + typed response |
| `deleteAsync(endpoint)` | `CompletableFuture<ApiResponse>` | Async DELETE |
| `deleteAsyncTyped(endpoint, Class<T>)` | `CompletableFuture<T>` | Async DELETE + typed |
| `patchSync(endpoint, String/T payload)` | `ApiResponse` | PATCH a body |
| `patchSyncTyped(endpoint, body, Class<T>)` | `T` | PATCH + typed response |
| `patchAsync(...)` / `patchAsyncTyped(...)` | future variants | Async PATCH |

### Core API (advanced cases)

| Method | Returns | Use when |
|---|---|---|
| `send(HttpMethod, ApiRequest)` | `ApiResponse` | Caller controls everything — including `requestId`. Used for retry correlation across attempts. |
| `sendTyped(HttpMethod, ApiRequest, Class<T>)` | `T` | Same, with typed response |
| `sendAsync(HttpMethod, ApiRequest)` | `CompletableFuture<ApiResponse>` | Async variant |
| `sendAsyncTyped(HttpMethod, ApiRequest, Class<T>)` | `CompletableFuture<T>` | Async + typed |

Convenience wrappers (above) all funnel through the core methods.

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

## Sharing a `request_id` across retries

When you wrap a call in `@Retryable` (or your own retry loop), the default API generates a fresh UUID per attempt — your retry attempts end up scattered across `api_log` with no correlation. To make them share a key, build the `ApiRequest` yourself and pass it to `send()`:

```java
@Retryable(retryFor = HttpServerErrorException.class, maxAttempts = 3)
public ChargeResult charge(ChargeRequest input) {
    ApiRequest req = ApiRequest.builder()
            .endpoint("/charges")
            .payload(objectMapper.writeValueAsString(input))
            .requestId(input.getChargeId())      // same across retries
            .build();
    return api.sendTyped(HttpMethod.POST, req, ChargeResult.class);
}
```

All `api_log` rows for that retry chain now share `request_id`, so you can pull the timeline with `WHERE request_id = '<chargeId>'`.

See [Retry handling](retry-handling.md) for the full pattern including manual event publishing.

## PUT / DELETE / PATCH

Same shape as POST/GET. Quick examples:

```java
// PUT — replace a resource
User updated = api.putSyncTyped("/users/1", user, User.class);

// DELETE — usually no body, may return JSON
ApiResponse r = api.deleteSync("/users/1");
DeletionReceipt dr = api.deleteSyncTyped("/users/1", DeletionReceipt.class);

// PATCH — partial update
User patched = api.patchSyncTyped("/users/1", Map.of("email", "new@example.com"), User.class);
```

Async variants exist for all of them (`putAsync`, `deleteAsyncTyped`, etc.).

## Roadmap

Not yet supported (PRs welcome):

- Per-call header override
- Multipart / streaming uploads
- WebClient (reactive) variant
- Automatic retry-context propagation (currently caller plumbs `requestId` via `ApiRequest`)
