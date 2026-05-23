# api-log-spring-boot-starter

**English** · [한국어](README.ko.md)

> Event-driven API call logging for Spring Boot. Async event pipeline with PostgreSQL JSONB storage.

[![Maven Central](https://img.shields.io/maven-central/v/kr.devslab/api-log-core.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/kr.devslab/api-log-core)
[![CI](https://github.com/devslab-kr/api-log/actions/workflows/ci.yml/badge.svg)](https://github.com/devslab-kr/api-log/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/devslab-kr/api-log/branch/master/graph/badge.svg)](https://codecov.io/gh/devslab-kr/api-log)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5+-green.svg)](https://spring.io/projects/spring-boot)

📖 **[Documentation → api-log.devslab.kr](https://api-log.devslab.kr/)**

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
- **Retry-aware schema** — `RETRY_ERROR` event + `retry_count` / `is_retry` columns for tracking flaky calls. The listener also retries its own log writes 3× on transient DB failures.
- **Virtual Threads ready** — designed for Java 21+ async with low memory footprint
- **Drop-in starter** — auto-configuration registers all beans behind `@ConditionalOnMissingBean`

## Architecture

```
Caller code
   ↓
RestApiClientUtil / ReactiveApiClientUtil  (or your own HTTP client)
   ↓ publishEvent
ApplicationEventPublisher
   ↓ @EventListener (virtual threads)
ApiEventListener  (api-log-core)
   ↓ ApiLogWriter (SPI)
   ├─ JpaApiLogWriter      (api-log-jpa)
   ├─ R2dbcApiLogWriter    (api-log-r2dbc)
   └─ MybatisApiLogWriter  (api-log-mybatis)
        ↓
   PostgreSQL  (api_log · JSONB columns)
```

## Installation

v3.0.0 splits the starter into four artifacts: a backend-agnostic core, plus
one of three persistence backends. Add **`api-log-core` plus exactly one
backend** to your build:

| Coordinate | When to use it |
| --- | --- |
| `kr.devslab:api-log-jpa` | Servlet / JPA app (the v0.5.x drop-in) |
| `kr.devslab:api-log-r2dbc` | WebFlux / R2DBC app — no JDBC pull-in |
| `kr.devslab:api-log-mybatis` | Already on MyBatis, don't want JPA |

Each backend artifact transitively depends on `api-log-core`, so one
coordinate is enough.

### Maven

```xml
<!-- JPA (most common — drop-in for v0.5.x) -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>api-log-jpa</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- ...or R2DBC for reactive apps -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>api-log-r2dbc</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- ...or MyBatis -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>api-log-mybatis</artifactId>
    <version>3.0.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("kr.devslab:api-log-jpa:3.0.0")
// or "kr.devslab:api-log-r2dbc:3.0.0"
// or "kr.devslab:api-log-mybatis:3.0.0"
```

## Configuration

```yaml
api:
  log:
    enabled: true              # default — set to false to disable the whole infrastructure
    schema:
      management: builtin      # default — see "Schema" below
```

You bring your own:

- `DataSource` pointing at a PostgreSQL database
- `ObjectMapper` bean (Spring Boot's auto-config is fine)

The `api_log` table is created for you on first boot — no other setup needed.

### Schema

`api.log.schema.management` selects how the `api_log` table is provisioned:

- **`builtin`** (default) — the starter runs `CREATE TABLE IF NOT EXISTS` on startup. Idempotent, requires no migration tool. Just works.
- **`flyway`** — register with the consumer's Flyway flow (`flyway_schema_history` tracks the migration). Requires `flyway-core` on the classpath.
- **`none`** — the starter does not touch the schema. Apply the DDL yourself via Liquibase / manual `psql` / your own flow. See the [Schema reference](https://api-log.devslab.kr/reference/schema/) for the SQL.

Full installation guide: [api-log.devslab.kr/getting-started/installation](https://api-log.devslab.kr/getting-started/installation/).

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
| `event_type`    | VARCHAR(50)  | `INITIATED`, `SUCCESS`, `ERROR`, `RETRY_ERROR` |
| `request_id`    | VARCHAR(36)  | UUID correlation id                            |
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
