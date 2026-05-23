# JPA backend

The JPA backend (`api-log-jpa`) is the default — it's what v0.5.x shipped, now
packaged as one of three choices. Pick it when your application already uses
Spring Data JPA, or when you don't have a strong reason to go reactive.

## When to pick it

- You're on a Spring MVC + JPA stack.
- You want the v0.5.x behavior unchanged — `ApiLogEntity`,
  `ApiLogRepository`, and Hibernate's `@JdbcTypeCode(SqlTypes.JSON)` mapping
  for the JSONB columns.
- You want to query the audit log via the same `JpaRepository` infrastructure
  the rest of your app uses.

If you're on WebFlux + R2DBC, prefer [`api-log-r2dbc`](r2dbc-backend.md). If
you're on MyBatis, prefer [`api-log-mybatis`](mybatis-backend.md). The
`api_log` schema is identical across all three.

## Install

=== "Maven"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-jpa</artifactId>
        <version>3.0.1</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    implementation("kr.devslab:api-log-jpa:3.0.1")
    ```

`api-log-jpa` transitively pulls in `api-log-core` (events, listener, HTTP
utilities) plus `spring-boot-starter-data-jpa` and the PostgreSQL JDBC driver.
Nothing else to add — Flyway is optional and only matters if you switch
`api.log.schema.management` to `flyway`.

## What gets registered

When the JPA backend is on the classpath and `api.log.enabled=true` (the
default), `ApiLogJpaAutoConfiguration` activates and registers:

| Bean | Purpose |
| --- | --- |
| `JpaApiLogWriter` | The `ApiLogWriter` implementation the core listener routes events through |
| `ApiLogJpaSchemaInitializer` | Runs `V1.0__create_api_log.sql` against your `DataSource` (BUILTIN mode only) |
| `ApiLogRepository` (via `@EnableJpaRepositories`) | Spring Data repository for `ApiLogEntity` |
| `ApiLogFlywayConfigurationCustomizer` | Appends `classpath:db/api-log` to Flyway's locations (FLYWAY mode only) |

`@EntityScan(basePackageClasses = ApiLogEntity.class)` is wired automatically
— you don't need to add `ApiLogEntity` to your own `@SpringBootApplication`'s
package scan.

## The entity

```java
package kr.devslab.apilog.jpa.model;

@Entity
@Table(name = "api_log")
public class ApiLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventType;
    private String requestId;
    private String endpoint;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode response;

    private Integer statusCode;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode errorMessage;

    private LocalDateTime timestamp;
    private Integer retryCount;
    private Boolean isRetry;
}
```

The three JSONB columns (`payload`, `response`, `error_message`) are mapped as
Jackson `JsonNode` via `@JdbcTypeCode(SqlTypes.JSON)` — Hibernate delegates to
the PostgreSQL dialect's JSONB binder, so the round-trip preserves JSON
structure (not just text).

## Querying logs from your code

The bundled `ApiLogRepository` exposes simple lookups:

```java
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final ApiLogRepository repo;

    public List<ApiLogEntity> timelineFor(String requestId) {
        return repo.findByRequestId(requestId);
    }

    public List<ApiLogEntity> errorsAt(String endpoint) {
        return repo.findByEndpoint(endpoint).stream()
                .filter(e -> "ERROR".equals(e.getEventType()))
                .toList();
    }
}
```

For richer queries — JSONB operators, GIN-indexed payload searches, error-rate
aggregations — go to the [Querying logs guide](querying-logs.md), which
applies to all three backends since the table schema is the same.

## Transaction semantics

Every `JpaApiLogWriter` method runs in `@Transactional(propagation = REQUIRES_NEW)`:

```java
@Override
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void writeInitiated(ApiCallInitiatedEvent event) { ... }
```

This is deliberate. An audit log write must never roll back with the caller's
business transaction — if the caller's `@Transactional` later fails and rolls
back, the `INITIATED` row stays. You should be able to query `api_log` and see
that the call actually went out, regardless of what happened to the rest of
the unit of work.

## Schema management

The default is `api.log.schema.management=builtin` — on startup the bundled
`V1.0__create_api_log.sql` runs against your `DataSource` via Spring Boot's
`DataSourceScriptDatabaseInitializer`. The DDL uses `IF NOT EXISTS`, so it's
idempotent.

```yaml title="application.yml"
api:
  log:
    schema:
      management: builtin   # default
```

To switch to Flyway (so the migration is recorded in `flyway_schema_history`):

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

```yaml title="application.yml"
api:
  log:
    schema:
      management: flyway
```

`ApiLogFlywayConfigurationCustomizer` appends `classpath:db/api-log` to your
existing `spring.flyway.locations` — your own migrations and ours run from one
sweep, share one history table.

To opt out entirely (apply the DDL yourself):

```yaml
api:
  log:
    schema:
      management: none
```

Full strategy details and the raw DDL are in the
[Schema reference](../reference/schema.md).

## Overriding the writer

If you need to customize how rows are written (extra columns, masking
payloads, a different table), provide your own `ApiLogWriter` bean — the
backend's `@ConditionalOnMissingBean(ApiLogWriter.class)` backs off:

```java
@Bean
public ApiLogWriter customWriter(ApiLogRepository repo, PayloadJsonMapper json,
                                  PayloadMasker masker) {
    return new MaskingJpaApiLogWriter(repo, json, masker);
}
```

A common pattern is to wrap the bundled writer:

```java
public class MaskingJpaApiLogWriter implements ApiLogWriter {

    private final ApiLogWriter delegate;
    private final PayloadMasker masker;

    public MaskingJpaApiLogWriter(ApiLogRepository repo, PayloadJsonMapper json,
                                   PayloadMasker masker) {
        this.delegate = new JpaApiLogWriter(repo, json);
        this.masker = masker;
    }

    @Override
    public void writeInitiated(ApiCallInitiatedEvent event) {
        delegate.writeInitiated(masker.mask(event));
    }
    // ... same for writeSuccess / writeError
}
```

## See also

- [Querying logs](querying-logs.md) — JSONB operator recipes, indexes, error
  rates.
- [Publishing events manually](publishing-events.md) — bring your own HTTP
  client, just use the events.
- [Retry handling](retry-handling.md) — `RETRY_ERROR` semantics, listener
  log-write retries.
- [Schema reference](../reference/schema.md) — column types, indexes, raw DDL.
