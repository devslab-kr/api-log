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
        <version>0.1.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("kr.devslab:api-log-spring-boot-starter:0.1.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'kr.devslab:api-log-spring-boot-starter:0.1.0'
    }
    ```

!!! note "Pre-release"
    Until 0.1.0 hits Maven Central, use the `0.1.0-SNAPSHOT` from Sonatype OSSRH or build from source: `./mvnw install`.

## What the starter pulls in

The starter brings these for you transitively:

- `spring-boot-starter-data-jpa` (the `ApiLogRepository`)
- `spring-boot-starter-web` (the bundled `RestApiClientUtil`)
- `spring-retry` (retry-aware logging)
- `jackson-module-blackbird` (high-throughput JSON serialization)
- `flyway-core` + `flyway-database-postgresql` (auto-applies the `api_log` schema)
- `postgresql` JDBC driver (runtime)

## What you bring yourself

- A **PostgreSQL `DataSource`** — the starter doesn't configure database connection details for you
- An **`ObjectMapper` bean** — Spring Boot's auto-configured one is sufficient

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
    enabled: true   # default — set to false to disable the listener at runtime
```

## What auto-configuration does

When the starter is on the classpath and `api.log.enabled` is `true` (the default), `ApiLogAutoConfiguration` activates and registers:

- `ApiLogService` — the persistence orchestrator (gated on an `ObjectMapper` bean)
- `ApiEventListener` — the `@EventListener` (async) that bridges events to the service
- `RetryConfig` — enables `@EnableRetry` for Spring Retry integration
- JPA `@EntityScan` and `@EnableJpaRepositories` scoped to `kr.devslab.apilog.model` and `kr.devslab.apilog.repository`

All beans use `@ConditionalOnMissingBean`. Define your own to override.

## Schema

The bundled Flyway migration `V1.0__create_api_log.sql` creates the `api_log` table and its indexes the first time your app starts. If you use Flyway in your project, it will pick this up automatically — see [Reference / Schema](../reference/schema.md) for the columns.

## Verifying the install

After adding the dependency and configuring your `DataSource`, start your application. You should see Flyway apply the migration:

```text
o.f.c.i.command.DbMigrate : Migrating schema "public" to version "1.0 - create api log"
o.f.c.i.command.DbMigrate : Successfully applied 1 migration to schema "public"
```

And the `api_log` table now exists:

```sql
\d api_log
```

Continue to the [Quickstart](quickstart.md) to make your first logged call.
