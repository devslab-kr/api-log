# MyBatis backend

The MyBatis backend (`api-log-mybatis`) writes audit rows through a
`@Mapper`-annotated interface. Pick it when your application is already on
MyBatis and you don't want to drag JPA / Hibernate in just for the audit log.

## When to pick it

- You're already on MyBatis (any web stack — Servlet or WebFlux+JDBC).
- You want one ORM in your project. Adding JPA *just* for audit logging means
  two persistence frameworks, two transaction managers, two sets of conventions
  — usually not worth it.

If you're on JPA, prefer [`api-log-jpa`](jpa-backend.md). If you're on
WebFlux + R2DBC and want pure-reactive persistence,
[`api-log-r2dbc`](r2dbc-backend.md) is the right pick. The `api_log` schema
is identical across all three.

## Install

=== "Maven"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-mybatis</artifactId>
        <version>0.6.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    implementation("kr.devslab:api-log-mybatis:3.0.0")
    ```

`api-log-mybatis` transitively pulls in `api-log-core`,
`mybatis-spring-boot-starter:3.0.4` (the Spring Boot 3.x compatible line),
`spring-jdbc`, and the PostgreSQL JDBC driver. You still need a configured
`DataSource` — Spring Boot's defaults via `spring.datasource.*` work
unchanged.

## What gets registered

When MyBatis (`org.apache.ibatis.session.SqlSessionFactory`) is on the
classpath and `api.log.enabled=true`, `ApiLogMybatisAutoConfiguration`
activates and registers:

| Bean | Purpose |
| --- | --- |
| `MybatisApiLogWriter` | The `ApiLogWriter` implementation the core listener routes events through |
| `ApiLogMapper` (via `@MapperScan`) | The MyBatis `@Mapper` carrying the INSERT SQL |
| `ApiLogMybatisSchemaInitializer` | Runs `V1.0__create_api_log.sql` against the `DataSource` (BUILTIN mode only) |

`@MapperScan(basePackageClasses = ApiLogMapper.class)` is applied
automatically. If your application already has its own `@MapperScan` for
different packages, ours composes additively — both scans run.

## The mapper

```java
package kr.devslab.apilog.mybatis.mapper;

@Mapper
public interface ApiLogMapper {

    @Insert("""
            INSERT INTO api_log
                (event_type, request_id, endpoint, payload, response,
                 status_code, error_message, timestamp, retry_count, is_retry)
            VALUES
                (#{eventType},
                 #{requestId},
                 #{endpoint},
                 CAST(#{payload,jdbcType=VARCHAR} AS jsonb),
                 CAST(#{response,jdbcType=VARCHAR} AS jsonb),
                 #{statusCode,jdbcType=INTEGER},
                 CAST(#{errorMessage,jdbcType=VARCHAR} AS jsonb),
                 #{timestamp},
                 #{retryCount},
                 #{isRetry})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(ApiLogRow row);

    @Select("""
            SELECT id, event_type AS eventType, request_id AS requestId, endpoint,
                   payload::text AS payload, response::text AS response,
                   status_code AS statusCode, error_message::text AS errorMessage,
                   timestamp, retry_count AS retryCount, is_retry AS isRetry
            FROM api_log WHERE request_id = #{requestId} ORDER BY id ASC
            """)
    List<ApiLogRow> findByRequestId(String requestId);
}
```

### Why `CAST(...,jdbcType=VARCHAR) AS jsonb`

PostgreSQL won't implicitly cast a Java `String` parameter into a `JSONB`
column. Two ways to bridge that:

1. **Custom `TypeHandler`** — boilerplate, requires registering the handler
   per JSONB column.
2. **Explicit cast in SQL** — what this mapper does. One line per column,
   nothing else to wire up.

The `jdbcType=VARCHAR` annotation forces a VARCHAR binding even when the
value is `null`, side-stepping PostgreSQL's "could not determine data type of
parameter" error on null JSONB binds.

### The row type

```java
public class ApiLogRow {
    private Long id;
    private String eventType;
    private String requestId;
    private String endpoint;
    private String payload;        // JSON string — canonical form from PayloadJsonMapper
    private String response;       // JSON string
    private Integer statusCode;
    private String errorMessage;   // JSON string
    private LocalDateTime timestamp;
    private Integer retryCount;
    private Boolean isRetry;
}
```

The three "JSON" fields are plain `String`. `PayloadJsonMapper.toJsonString()`
(from `api-log-core`) produces the canonical JSON form, and the mapper's cast
turns it into JSONB on insert.

## Transaction semantics

`MybatisApiLogWriter` methods run in `@Transactional(propagation = REQUIRES_NEW)`:

```java
@Override
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void writeInitiated(ApiCallInitiatedEvent event) { ... }
```

This matches the JPA backend's contract — audit writes must not roll back
with the caller's outer transaction. The audit row is committed on its own,
regardless of what the rest of the unit of work does.

## Schema management

The default is `api.log.schema.management=builtin`. The MyBatis backend uses
the JDBC-based `DataSourceScriptDatabaseInitializer` (same approach as the
JPA backend), since MyBatis itself runs on JDBC. The DDL uses `IF NOT EXISTS`,
so re-running on every boot is a no-op.

```yaml title="application.yml"
api:
  log:
    schema:
      management: builtin   # default
```

Flyway integration **isn't bundled** in `api-log-mybatis` — only `api-log-jpa`
ships the `FlywayConfigurationCustomizer`. For MyBatis apps that want Flyway
to manage the table:

- Add Flyway to your dependencies (`flyway-core` + `flyway-database-postgresql`).
- Set `spring.flyway.locations` to include `classpath:db/api-log` alongside
  your own:

  ```yaml
  spring:
    flyway:
      locations:
        - classpath:db/migration   # your own
        - classpath:db/api-log     # ours
  ```

- Set `api.log.schema.management=none` so the BUILTIN initializer doesn't
  fight Flyway's own bootstrap.

`none` (apply the DDL yourself, e.g. via Liquibase or `psql`) is also valid:

```yaml
api:
  log:
    schema:
      management: none
```

## Reading rows back

The bundled `findByRequestId` covers the common "timeline of one call"
query. For everything else, write your own queries on `ApiLogMapper` (in your
project's own mapper that extends the bundled one, or in a separate mapper):

```java
@Mapper
public interface MyApiLogQueries {

    @Select("""
            SELECT COUNT(*) FILTER (WHERE event_type = 'ERROR') * 100.0 / COUNT(*)
            FROM api_log
            WHERE endpoint = #{endpoint}
              AND timestamp > NOW() - INTERVAL '1 hour'
            """)
    Double errorRateLastHour(String endpoint);
}
```

The full JSONB query playbook (operators, GIN indexes, error rates) is in the
[Querying logs guide](querying-logs.md) — backend-independent.

## Overriding the writer

To customize how rows are written, provide your own `ApiLogWriter` bean —
the backend's `@ConditionalOnMissingBean(ApiLogWriter.class)` backs off:

```java
@Bean
public ApiLogWriter customWriter(ApiLogMapper mapper, PayloadJsonMapper json,
                                  PayloadMasker masker) {
    return new MaskingMybatisApiLogWriter(mapper, json, masker);
}
```

Wrapping the bundled writer is usually enough:

```java
public class MaskingMybatisApiLogWriter implements ApiLogWriter {

    private final ApiLogWriter delegate;
    private final PayloadMasker masker;

    public MaskingMybatisApiLogWriter(ApiLogMapper mapper, PayloadJsonMapper json,
                                       PayloadMasker masker) {
        this.delegate = new MybatisApiLogWriter(mapper, json);
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

- [Querying logs](querying-logs.md) — JSONB operator recipes, GIN indexes,
  error rates.
- [Publishing events manually](publishing-events.md) — bring your own HTTP
  client, just use the events.
- [Retry handling](retry-handling.md) — `RETRY_ERROR` semantics, listener
  log-write retries.
- [Schema reference](../reference/schema.md) — column types, indexes, raw DDL.
