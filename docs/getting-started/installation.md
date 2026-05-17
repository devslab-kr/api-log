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
        <version>0.2.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("kr.devslab:api-log-spring-boot-starter:0.2.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'kr.devslab:api-log-spring-boot-starter:0.2.0'
    }
    ```

!!! tip "Latest version"
    Replace `0.2.0` with the latest from [Maven Central](https://central.sonatype.com/artifact/kr.devslab/api-log-spring-boot-starter).

## What the starter pulls in

The starter brings these for you transitively:

- `spring-boot-starter-data-jpa` (the `ApiLogRepository`)
- `spring-boot-starter-web` (the bundled `RestApiClientUtil`)
- `spring-retry` (retry-aware logging)
- `jackson-module-blackbird` (high-throughput JSON serialization)
- `postgresql` JDBC driver (runtime)

!!! info "Flyway is optional (as of v0.2.0)"
    Flyway is no longer a transitive dependency. If you want the bundled migration to run automatically (see [Schema management](#schema-management) below), add it yourself:

    === "Maven"

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

    === "Gradle (Kotlin DSL)"

        ```kotlin
        implementation("org.flywaydb:flyway-core")
        runtimeOnly("org.flywaydb:flyway-database-postgresql")
        ```

## What you bring yourself

- A **PostgreSQL `DataSource`** — the starter doesn't configure database connection details for you
- An **`ObjectMapper` bean** — Spring Boot's auto-configured one is sufficient
- A way to create the `api_log` table — either apply the DDL yourself, or opt in to the bundled Flyway migration (see below)

```yaml title="application.yml"
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_db
    username: your_user
    password: your_password
  threads:
    virtual:
      enabled: true   # recommended on Java 21+

api:
  log:
    enabled: true              # default — false disables the whole infrastructure
    schema:
      management: none         # default — see "Schema management" below
```

## What auto-configuration does

When the starter is on the classpath and `api.log.enabled` is `true` (the default), `ApiLogAutoConfiguration` activates and registers:

- `ApiLogService` — the persistence orchestrator (gated on an `ObjectMapper` bean)
- `ApiEventListener` — the `@EventListener` (async) that bridges events to the service
- `RetryConfig` — enables `@EnableRetry` for Spring Retry integration
- JPA `@EntityScan` and `@EnableJpaRepositories` scoped to `kr.devslab.apilog.model` and `kr.devslab.apilog.repository`

All beans use `@ConditionalOnMissingBean`. Define your own to override.

## Schema management { #schema-management }

The `api_log` table is **not** created automatically. You choose how it's provisioned via `api.log.schema.management`:

=== "NONE (default) — apply DDL yourself"

    Take the [Schema reference](../reference/schema.md) SQL and put it in:

    - your own Flyway migration, or
    - your own Liquibase changelog, or
    - a manual `psql` run during deployment, or
    - whatever schema flow you already have

    This is the **default** because most production teams already manage migrations and don't want a third-party library reaching into their schema.

=== "FLYWAY — let the starter manage it"

    Add Flyway to your dependencies (see above), then:

    ```yaml title="application.yml"
    api:
      log:
        schema:
          management: flyway
    ```

    The starter registers a `FlywayConfigurationCustomizer` that appends `classpath:db/api-log` to your existing `spring.flyway.locations`. Your own migrations continue to run alongside ours — no collision, no conflict.

    The bundled migration `V1.0__create_api_log.sql` creates the `api_log` table and the indexes on `request_id` and `timestamp`.

## Verifying the install

After adding the dependency and starting your app, two things confirm the install:

1. **The auto-configuration loads**. With `--debug` you'll see `ApiLogAutoConfiguration matched`.
2. **The `api_log` table exists** — either because you applied the DDL manually, or because Flyway logged:
    ```text
    o.f.c.i.command.DbMigrate : Migrating schema "public" to version "1.0 - create api log"
    o.f.c.i.command.DbMigrate : Successfully applied 1 migration to schema "public"
    ```

Continue to the [Quickstart](quickstart.md) to make your first logged call.
