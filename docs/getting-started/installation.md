# Installation

## Requirements

- **Java 21+** (Virtual Threads are recommended but not required)
- **Spring Boot 3.5+**
- **PostgreSQL 15+** (the storage layer relies on JSONB; older versions work but lose some operators)

## Adding the dependency

v0.6.0 splits the starter into a backend-agnostic core plus one persistence
backend per artifact. **Pick one row from the table below — that's it; the
backend artifact pulls in `api-log-core` transitively.**

| You are using… | Add this artifact |
| --- | --- |
| Spring MVC + JPA (the v0.5.x default) | `kr.devslab:api-log-jpa` |
| WebFlux + R2DBC (reactive end-to-end) | `kr.devslab:api-log-r2dbc` |
| MyBatis (any web stack) | `kr.devslab:api-log-mybatis` |

=== "Maven"

    ```xml
    <!-- JPA backend — drop-in for v0.5.x setups -->
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-jpa</artifactId>
        <version>0.6.0</version>
    </dependency>

    <!-- ...or, for reactive apps -->
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-r2dbc</artifactId>
        <version>0.6.0</version>
    </dependency>

    <!-- ...or, MyBatis -->
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-mybatis</artifactId>
        <version>0.6.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        // JPA — drop-in for v0.5.x setups
        implementation("kr.devslab:api-log-jpa:0.6.0")
        // or "kr.devslab:api-log-r2dbc:0.6.0"
        // or "kr.devslab:api-log-mybatis:0.6.0"
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'kr.devslab:api-log-jpa:0.6.0'
        // or 'kr.devslab:api-log-r2dbc:0.6.0'
        // or 'kr.devslab:api-log-mybatis:0.6.0'
    }
    ```

!!! tip "Latest version"
    Replace `0.6.0` with the latest from [Maven Central](https://central.sonatype.com/artifact/kr.devslab/api-log-core).

!!! info "Upgrading from v0.5.x?"
    Swap the old `api-log-spring-boot-starter` coordinate for `api-log-jpa`
    (same JPA backend, same `api_log` rows). A few packages were renamed —
    see the [v0.6.0 changelog](../changelog.md#060--multi-module-split-gradle-pluggable-jpa--r2dbc--mybatis-backends)
    for the complete mapping.

## What each artifact pulls in

**`api-log-core`** (always — pulled transitively by every backend artifact):

- `spring-boot-starter` (`@EventListener`, `@EnableAsync`, `ApplicationEventPublisher`)
- `spring-retry` + `spring-boot-starter-aop` (listener `@Retryable` log-write retries)
- `jackson-databind` + `jackson-module-blackbird` (the JSONB payload serializer)
- `spring-web` / `spring-webflux` (compile-only — the HTTP utilities reference them but require the consumer's classpath to actually include one or the other)

**`api-log-jpa`** adds:

- `spring-boot-starter-data-jpa` (the `ApiLogRepository`)
- `postgresql` JDBC driver (runtime)
- `flyway-core` (compile-only — only activated when `api.log.schema.management=flyway`)

**`api-log-r2dbc`** adds:

- `spring-r2dbc` (`DatabaseClient`)
- `r2dbc-postgresql` (runtime)
- `reactor-core`

No JDBC driver — pure reactive.

**`api-log-mybatis`** adds:

- `mybatis-spring-boot-starter:3.0.4`
- `spring-jdbc`
- `postgresql` JDBC driver (runtime)

## What you bring yourself

- A **PostgreSQL `DataSource`** — the starter doesn't configure database connection details for you
- An **`ObjectMapper` bean** — Spring Boot's auto-configured one is sufficient

That's it. With the default settings, the `api_log` table is created for you on first boot.

```yaml title="application.yml"
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_db
    username: your_user
    password: your_password
  threads:
    virtual:
      enabled: true   # recommended on Java 21+

# Both of these have working defaults — only set them when you want non-default behavior.
api:
  log:
    enabled: true              # default — false disables the whole infrastructure
    schema:
      management: builtin      # default — see "Schema management" below
```

## What auto-configuration does

When `api.log.enabled` is `true` (the default), three auto-configurations from
`api-log-core` activate plus one from whichever backend you picked.

From **`api-log-core`** (`ApiLogCoreAutoConfiguration`,
`RestApiClientAutoConfiguration`, `ReactiveApiClientAutoConfiguration`):

- `ApiEventListener` — the `@EventListener` that bridges events to the chosen `ApiLogWriter`
- `PayloadJsonMapper` — shared JSON helper for every writer
- `RetryConfig` — enables `@EnableRetry` so the listener's own `@Retryable` log-write retries work
- `apiLogJacksonCustomizer` — adds Blackbird to Spring Boot's default `ObjectMapper`
- `apiLogVirtualThreadExecutor` / `apiLogPlatformThreadExecutor` — async executor for the listener (virtual threads when enabled)
- `RestApiClientUtil` (when `RestClient` is on the classpath)
- `ReactiveApiClientUtil` (when `WebClient` is on the classpath)

From the **backend artifact**:

- `ApiLogWriter` implementation — `JpaApiLogWriter` / `R2dbcApiLogWriter` / `MybatisApiLogWriter`, depending on which artifact you added
- Schema initializer (BUILTIN mode) — JDBC-based for `:jpa` + `:mybatis`, pure-reactive for `:r2dbc`
- JPA `@EntityScan` + `@EnableJpaRepositories` (`:jpa` only) or `@MapperScan` (`:mybatis` only)

All beans use `@ConditionalOnMissingBean`. Define your own to override.

## Schema management { #schema-management }

How the `api_log` table is created depends on `api.log.schema.management`:

=== "BUILTIN (default) — the starter handles it"

    On every application startup, the starter runs the bundled `V1.0__create_api_log.sql`
    against your `DataSource`. The DDL uses `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`,
    so it's idempotent — re-running on every boot is a no-op once the table exists.

    No extra dependencies needed. No external migration tool. Just works.

    The schema is applied via Spring Boot's
    [`DataSourceScriptDatabaseInitializer`](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jdbc/init/DataSourceScriptDatabaseInitializer.html),
    so it runs BEFORE JPA's entity validation — a fresh database won't fail Hibernate's
    `ddl-auto=validate` check at boot.

=== "FLYWAY — register with consumer Flyway"

    Add Flyway to your dependencies:

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

    Then set:

    ```yaml title="application.yml"
    api:
      log:
        schema:
          management: flyway
    ```

    The starter registers a `FlywayConfigurationCustomizer` that appends `classpath:db/api-log` to your existing `spring.flyway.locations`. Your own migrations run alongside ours, and `flyway_schema_history` tracks `V1.0__create_api_log` like any other migration.

    Pick this if your team treats the `flyway_schema_history` row as the authoritative record of schema changes.

=== "NONE — apply DDL yourself"

    The starter does not touch the schema. Take the SQL from the [Schema reference](../reference/schema.md) and put it in:

    - your own Liquibase changelog, or
    - a manual `psql` run during deployment, or
    - your own startup script

    ```yaml title="application.yml"
    api:
      log:
        schema:
          management: none
    ```

    Pick this if your team's policy forbids third-party libraries from touching the schema, or if the table is already provisioned by infrastructure you don't want to overlap with.

## Verifying the install

After adding the dependency and starting your app:

1. **The auto-configuration loads.** With `--debug` you'll see `ApiLogAutoConfiguration matched`.
2. **The `api_log` table exists** — for BUILTIN, this happens automatically. For FLYWAY, you'll see Flyway's standard "applied 1 migration" log line. For NONE, you applied it yourself.

Continue to the [Quickstart](quickstart.md) to make your first logged call.
