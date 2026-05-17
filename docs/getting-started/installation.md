# Installation

## Requirements

- **Java 21+** (Virtual Threads are recommended but not required)
- **Spring Boot 3.5+**
- **PostgreSQL 15+** (the storage layer relies on JSONB; older versions work but lose some operators)

## Adding the dependency

=== "Maven"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-spring-boot-starter</artifactId>
        <version>0.3.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("kr.devslab:api-log-spring-boot-starter:0.3.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'kr.devslab:api-log-spring-boot-starter:0.3.0'
    }
    ```

!!! tip "Latest version"
    Replace `0.3.0` with the latest from [Maven Central](https://central.sonatype.com/artifact/kr.devslab/api-log-spring-boot-starter).

## What the starter pulls in

The starter brings these for you transitively:

- `spring-boot-starter-data-jpa` (the `ApiLogRepository`)
- `spring-boot-starter-web` (the bundled `RestApiClientUtil`)
- `spring-retry` (retry-aware logging)
- `jackson-module-blackbird` (high-throughput JSON serialization)
- `postgresql` JDBC driver (runtime)

Flyway is **optional** — only needed if you set `api.log.schema.management=flyway` (see [Schema management](#schema-management) below). The default doesn't need it.

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

When the starter is on the classpath and `api.log.enabled` is `true` (the default), `ApiLogAutoConfiguration` activates and registers:

- `ApiLogService` — the persistence orchestrator (gated on an `ObjectMapper` bean)
- `ApiEventListener` — the `@EventListener` (async) that bridges events to the service
- `RetryConfig` — enables `@EnableRetry` for Spring Retry integration
- `ApiLogSchemaInitializer` — runs `CREATE TABLE IF NOT EXISTS` on startup (active for `schema.management=builtin`, the default)
- JPA `@EntityScan` and `@EnableJpaRepositories` scoped to `kr.devslab.apilog.model` and `kr.devslab.apilog.repository`

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
