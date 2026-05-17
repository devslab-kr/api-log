# api-log-spring-boot-starter

**English** · [한국어](README.ko.md)

> Event-driven API call logging for Spring Boot. Async event pipeline with PostgreSQL JSONB storage.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5+-green.svg)](https://spring.io/projects/spring-boot)

## What it does

Logs every outbound HTTP call from your service into PostgreSQL — request, response, errors, retries — through a non-blocking event pipeline. Drop the starter on the classpath, and any HTTP call made via the bundled `RestApiClientUtil` (or events you publish yourself) gets persisted as JSONB without slowing the caller down.

## At a glance

```java
@Service
public class UserService {

    private final RestApiClientUtil api;

    public UserService(RestApiClientUtil api) {
        this.api = api;
    }

    public User createUser(User newUser) {
        // The HTTP call is synchronous; logging fires async events behind the scenes.
        return api.postSyncTyped("/api/users", newUser, User.class);
    }
}
```

Every call lands one or more rows in `api_log`:

- **INITIATED** — request fired
- **SUCCESS** / **ERROR** — terminal outcome with status code and payload
- **RETRY_ERROR** — emitted for each retry attempt that failed

Bodies are stored as JSONB, so you can query them with `->`, `->>`, and GIN indexes.

## Features

- **Non-blocking** — log writes happen on a separate thread, never on the request path
- **PostgreSQL JSONB** — request/response/error bodies preserved as queryable JSON
- **Retry-aware** — Spring Retry integration; each retry recorded individually
- **Virtual Threads ready** — designed for Java 21+ async with low memory footprint
- **Drop-in starter** — auto-configuration registers all beans behind `@ConditionalOnMissingBean`

## Architecture

```
Caller code
   ↓
RestApiClientUtil  (or your own HTTP client)
   ↓ publishEvent
ApplicationEventPublisher
   ↓ @EventListener (async)
ApiEventListener
   ↓
ApiLogService
   ↓
ApiLogRepository  (JPA)
   ↓
PostgreSQL  (api_log · JSONB columns)
```

## Installation

### Maven

```xml
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>api-log-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Gradle

```kotlin
implementation("kr.devslab:api-log-spring-boot-starter:0.1.0-SNAPSHOT")
```

## Configuration

```yaml
api:
  log:
    enabled: true   # default: true
```

You bring your own:

- `DataSource` pointing at a PostgreSQL database
- `ObjectMapper` bean (Spring Boot's auto-config is fine)

The Flyway migration `V1.0__create_api_log.sql` ships with the starter — Spring Boot will pick it up if Flyway is on the classpath.

## Using `RestApiClientUtil`

```java
// GET
ApiResponse r = api.getSync("/api/users/1");
User user    = api.getSyncTyped("/api/users/1", User.class);

// POST
ApiResponse r  = api.postSync("/api/users", payload);
User created  = api.postSyncTyped("/api/users", payload, User.class);

// Async
CompletableFuture<ApiResponse> f = api.postAsync("/api/users", payload);
```

## Publishing events manually

Bring your own HTTP client and only use the logging side:

```java
@Service
@RequiredArgsConstructor
public class MyClient {

    private final ApplicationEventPublisher publisher;

    public void call() {
        ApiRequest req = ApiRequest.builder()
                .endpoint("/external/users")
                .payload("{\"name\":\"John\"}")
                .build();

        publisher.publishEvent(new ApiCallInitiatedEvent(this, req));
        try {
            ApiResponse res = doHttp(req);              // your HTTP call
            publisher.publishEvent(new ApiCallSuccessEvent(this, req, res));
        } catch (Exception e) {
            publisher.publishEvent(new ApiCallErrorEvent(this, req, e, 0, false));
        }
    }
}
```

## Schema

| Column          | Type         | Notes                                       |
|-----------------|--------------|---------------------------------------------|
| `id`            | BIGSERIAL    | PK                                          |
| `event_type`    | VARCHAR(20)  | `INITIATED`, `SUCCESS`, `ERROR`, `RETRY_ERROR` |
| `request_id`    | VARCHAR(255) | Correlation id                              |
| `endpoint`      | VARCHAR(255) | Target URL                                  |
| `payload`       | JSONB        | Request body                                |
| `response`      | JSONB        | Response body                               |
| `error_message` | JSONB        | Error details (if any)                      |
| `status_code`   | INTEGER      | HTTP status                                 |
| `timestamp`     | TIMESTAMP    | When the event fired                        |
| `retry_count`   | INTEGER      | `0` for the initial attempt                 |
| `is_retry`      | BOOLEAN      | `true` for retry attempts                   |

### Recommended indexes

```sql
CREATE INDEX idx_api_log_endpoint    ON api_log (endpoint);
CREATE INDEX idx_api_log_timestamp   ON api_log (timestamp DESC);
CREATE INDEX idx_api_log_payload_gin ON api_log USING GIN (payload);
CREATE INDEX idx_api_log_response_gin ON api_log USING GIN (response);
```

## Example queries

```sql
-- Error rate per endpoint, last 1 hour
SELECT endpoint,
       COUNT(*) FILTER (WHERE event_type = 'ERROR') * 100.0 / COUNT(*) AS error_rate
FROM api_log
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY endpoint
HAVING COUNT(*) > 10
ORDER BY error_rate DESC;
```

## Requirements

- Java 21+
- Spring Boot 3.5+
- PostgreSQL 15+ (for JSONB)

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

---

Built by [Devslab](https://devslab.kr) · Part of the [DevsLab open-source toolkit](https://github.com/devslab-kr).
