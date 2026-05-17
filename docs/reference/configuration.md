# Configuration

All configuration is under the `api.log` prefix. The starter ships with sensible defaults — most projects don't need to set anything.

## Properties

| Property | Type | Default | Description |
|---|---|---|---|
| `api.log.enabled` | `boolean` | `true` | Master switch. When `false`, `ApiLogAutoConfiguration` short-circuits and no beans are registered (no listener, no service, no `RestApiClientUtil`). |
| `api.log.schema.management` | `NONE` \| `FLYWAY` | `NONE` | How the `api_log` table is created. `NONE` — you apply the DDL yourself (see [Schema](schema.md)). `FLYWAY` — the starter appends `classpath:db/api-log` to `spring.flyway.locations` so the bundled migration runs alongside yours. `FLYWAY` requires `org.flywaydb:flyway-core` on the classpath (added by the consumer, since the starter declares it as optional). |

That's the entire surface area — the starter is intentionally minimal. Most behavior (retry policy, async executor, Flyway location) flows from standard Spring Boot configuration.

## Disabling at runtime

```yaml title="application.yml"
api:
  log:
    enabled: false
```

When disabled:

- `ApiEventListener` is not registered → events are dropped if anyone publishes them
- `ApiLogService` is not registered → no writes to `api_log`
- `RestApiClientUtil` is not registered (it depends on the service)
- Flyway still applies the migration (the table is created either way) — to skip the migration too, exclude `db/migration/V1.0__create_api_log.sql` from your Flyway locations

## Standard Spring Boot knobs that matter

These aren't `api-log` specific, but you'll likely tune them when running the starter in production:

### Async executor

The `@Async` listener uses Spring Boot's default `taskExecutor`. To customize it (e.g., bound the queue, set core/max pool size):

```yaml title="application.yml"
spring:
  task:
    execution:
      pool:
        core-size: 8
        max-size: 32
        queue-capacity: 1000
        keep-alive: 60s
      thread-name-prefix: api-log-
```

On Java 21 with Virtual Threads, the executor is replaced entirely:

```yaml title="application.yml"
spring:
  threads:
    virtual:
      enabled: true
```

This is recommended for `api-log` — Virtual Threads are perfect for the "lots of waiting on DB writes" workload.

### Flyway migration location

When `api.log.schema.management=flyway`, the starter appends `classpath:db/api-log` to your `spring.flyway.locations`. Your existing entries are preserved:

```yaml title="application.yml"
spring:
  flyway:
    locations:
      - classpath:db/migration       # your own migrations (Spring Boot default)
      # api-log's classpath:db/api-log is appended automatically by the starter
```

You don't need to declare `classpath:db/api-log` yourself. If you do (explicitly), it'll show up twice in the locations list — Flyway is fine with that, but it's noisy. Leave it out and let the customizer handle it.

### JPA properties

The starter relies on `spring-boot-starter-data-jpa`. You bring your own JPA config:

```yaml title="application.yml"
spring:
  jpa:
    hibernate:
      ddl-auto: validate     # recommended — Flyway owns the schema
    show-sql: false          # set true only when debugging
    properties:
      hibernate:
        jdbc:
          batch_size: 50     # the listener writes one row per event; batching rarely helps here
```

### DataSource pool

The listener writes one row per event under default settings. Sizing your HikariCP pool:

```yaml title="application.yml"
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

The exact numbers depend on traffic — start with the default 10, watch the `hikaricp_connections_active` metric, scale from there.

## Overriding the auto-configured beans

Define your own bean of the same type and the starter's `@ConditionalOnMissingBean` will back off:

```java
@Configuration
public class CustomApiLogConfig {

    @Bean
    public ApiLogService apiLogService(ApiLogRepository repo, ObjectMapper mapper) {
        return new MyCustomApiLogService(repo, mapper);   // your subclass
    }
}
```

Same pattern works for `ApiEventListener` and `RestApiClientUtil`.

## See also

- [Schema reference](schema.md) — `api_log` table columns
- [Events reference](events.md) — event types and shapes
