# R2DBC backend

The R2DBC backend (`api-log-r2dbc`) writes audit rows over a reactive
`ConnectionFactory` — no JDBC driver, no blocking I/O. Use it when your
application is built on Spring WebFlux + R2DBC and you want the audit log to
participate in the same reactive pipeline instead of forcing a JDBC bridge.

## When to pick it

- Your application stack is WebFlux + R2DBC.
- You explicitly don't want a JDBC driver on your runtime classpath.
- You're OK with the writer using `DatabaseClient` directly rather than going
  through a Spring Data R2DBC repository — that's the chosen trade-off to
  keep the dependency footprint minimal.

If you have a Servlet stack, JPA is more idiomatic — pick
[`api-log-jpa`](jpa-backend.md) instead. The two backends produce identical
rows in `api_log`.

## Install

=== "Maven"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-r2dbc</artifactId>
        <version>0.6.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    implementation("kr.devslab:api-log-r2dbc:0.6.0")
    ```

`api-log-r2dbc` transitively pulls in `api-log-core` plus `spring-r2dbc`
(`DatabaseClient`), `r2dbc-postgresql` (runtime), and `reactor-core`. **No
JDBC dependency** — Hibernate, HikariCP, and `spring-jdbc` stay off your
classpath unless something else in your app pulls them in.

You still need a `ConnectionFactory` bean configured for PostgreSQL — the
easiest way is Spring Boot's auto-configuration:

```yaml title="application.yml"
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/your_db
    username: your_user
    password: your_password
```

## What gets registered

When `ConnectionFactory` is on the classpath and `api.log.enabled=true`,
`ApiLogR2dbcAutoConfiguration` activates and registers:

| Bean | Purpose |
| --- | --- |
| `DatabaseClient` (`@ConditionalOnMissingBean`) | Built from the consumer's `ConnectionFactory` — skipped if Spring Boot already provided one |
| `R2dbcApiLogWriter` | The `ApiLogWriter` implementation the core listener routes events through |
| `ApiLogR2dbcSchemaInitializer` | Runs `V1.0__create_api_log.sql` reactively via Spring Boot's `R2dbcScriptDatabaseInitializer` (BUILTIN mode only) |

The schema initializer talks to `ConnectionFactory` directly — **no JDBC
DataSource is required**, even at boot. That's the v0.6.0 promise this
backend delivers: a fully reactive `api_log` install.

## How rows get written

`R2dbcApiLogWriter` doesn't go through a Spring Data repository. It calls
`DatabaseClient.sql(...)` with a parameterized INSERT, and subscribes inline
to make it fire-and-forget:

```java
spec.fetch()
        .rowsUpdated()
        .subscribe(
                rows -> { /* success — listener already logs at DEBUG */ },
                ex -> log.error("R2DBC api_log insert failed: requestId={}, eventType={}",
                        requestId, eventType, ex)
        );
```

The listener doesn't consume any returned reactive type — events are
fire-and-forget by design, and the @Async wrapping wouldn't propagate a `Mono`
usefully anyway. Subscription errors are logged but never rethrown — losing
one audit row must never break the consumer's outbound HTTP call.

### JSONB binding without `::jsonb` casts

The three JSONB columns (`payload`, `response`, `error_message`) are bound as
`R2dbcType.CLOB` (text):

```java
private static Object asJsonbParam(String value) {
    return value == null
            ? Parameters.in(R2dbcType.CLOB)
            : Parameters.in(R2dbcType.CLOB, value);
}
```

The PostgreSQL R2DBC driver handles the `TEXT → JSONB` implicit cast at the
column level, so no `::jsonb` cast is needed in the SQL — the INSERT stays
portable for the day another reactive dialect shows up.

## Schema management

The default is `api.log.schema.management=builtin`. The reactive initializer
uses Spring Boot's `R2dbcScriptDatabaseInitializer` and runs the bundled DDL
on first connection. `IF NOT EXISTS` makes it idempotent across boots.

```yaml title="application.yml"
api:
  log:
    schema:
      management: builtin   # default
```

**Flyway mode is not supported under R2DBC.** Flyway requires a JDBC
`DataSource`; reactive apps that want Flyway-managed schema should either:

- Install Spring Boot's standard Flyway auto-config alongside R2DBC (Flyway
  opens its own JDBC connection just for migrations — separate from your
  R2DBC pool — and the rest of the app stays pure-reactive after boot), and
  add `classpath:db/api-log` to `spring.flyway.locations` themselves; or
- Switch to the JPA backend if pure-reactive isn't a hard requirement.

`api.log.schema.management=none` (apply the DDL yourself) is also valid:

```yaml
api:
  log:
    schema:
      management: none
```

## Reactive HTTP client integration

The reactive backend pairs naturally with
[`ReactiveApiClientUtil`](reactive.md), which returns `Mono<ApiResponse>` and
publishes the same events `R2dbcApiLogWriter` consumes:

```java
@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ReactiveApiClientUtil api;

    public Mono<ChargeResult> charge(ChargeRequest input) {
        return api.postTyped("/charges", input, ChargeResult.class);
    }
}
```

End-to-end reactive: WebClient call → published events → R2DBC writer →
PostgreSQL. No blocking hop anywhere on the request path.

## Overriding the writer

If you need to customize how rows are written (masking, extra columns,
different table), define your own `ApiLogWriter` bean — the backend's
`@ConditionalOnMissingBean(ApiLogWriter.class)` backs off:

```java
@Bean
public ApiLogWriter customWriter(DatabaseClient client, PayloadJsonMapper json) {
    return new TenantAwareR2dbcApiLogWriter(client, json, tenantContext);
}
```

A common pattern is delegation:

```java
public class TenantAwareR2dbcApiLogWriter implements ApiLogWriter {

    private final ApiLogWriter delegate;
    private final TenantContext tenants;

    public TenantAwareR2dbcApiLogWriter(DatabaseClient client, PayloadJsonMapper json,
                                         TenantContext tenants) {
        this.delegate = new R2dbcApiLogWriter(client, json);
        this.tenants = tenants;
    }

    @Override
    public void writeInitiated(ApiCallInitiatedEvent event) {
        delegate.writeInitiated(annotateTenant(event));
    }
    // ... same for writeSuccess / writeError
}
```

## Querying logs

You're not given a repository abstraction in this backend — query
`DatabaseClient` directly when you need to read rows back:

```java
public Flux<Map<String, Object>> timelineFor(String requestId) {
    return databaseClient.sql("""
                    SELECT event_type, endpoint, status_code, timestamp
                    FROM api_log WHERE request_id = :rid ORDER BY id ASC
                    """)
            .bind("rid", requestId)
            .fetch()
            .all();
}
```

For deeper queries (JSONB operators, GIN indexes, error rates), see the
[Querying logs guide](querying-logs.md) — the SQL is the same regardless of
backend.

## See also

- [Reactive HTTP client](reactive.md) — `ReactiveApiClientUtil`, the
  WebClient-backed companion that publishes the events this writer consumes.
- [Querying logs](querying-logs.md) — JSONB operator recipes, indexes, error
  rates.
- [Schema reference](../reference/schema.md) — column types, indexes, raw DDL.
