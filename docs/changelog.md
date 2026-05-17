# Changelog

All notable changes to `api-log-spring-boot-starter` are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] — PUT / DELETE / PATCH + retry-correlation via core API

### Added

- **PUT / DELETE / PATCH convenience methods on `RestApiClientUtil`** — 12 new methods following the existing GET/POST patterns: `putSync`, `putSyncTyped`, `putAsync`, `putAsyncTyped`, `deleteSync`, `deleteSyncTyped`, `deleteAsync`, `deleteAsyncTyped`, `patchSync`, `patchSyncTyped`, `patchAsync`, `patchAsyncTyped` (each with the appropriate `String` / typed-body / typed-response overloads).
- **Core `send` / `sendAsync` / `sendTyped` / `sendAsyncTyped` API** taking `(HttpMethod, ApiRequest)` directly. Lets callers supply an explicit `requestId` so retry attempts share a correlation key — fills the gap documented in the v0.4.0 retry-handling guide.

### Changed (internal — no public API change)

- `RestApiClientUtil` refactored so all 22 public methods funnel through the four core `send*` methods. ~270 lines of duplicated try/catch/event-publish boilerplate collapsed into one place. Behavior identical to v0.4.0.

### Tests

- New `RestApiClientUtilRoutingTest` verifies each verb method routes through the correct `HttpMethod` and that `send(HttpMethod, ApiRequest)` respects a caller-provided `requestId`.

### Migration from v0.4.0

Fully backward-compatible — all v0.4.0 method signatures and behaviors are preserved. The new methods are additive.

## [0.4.0] — Bug fixes: real status codes, structured errors, honest retry docs

### Fixed

- **`RestApiClientUtil` raw methods now report the actual HTTP status code.** `getSync`, `postSync(String, String)`, `getAsync`, `postAsync(String, String)` were hardcoding `statusCode = 200` regardless of the real response (201, 204, etc. all stored as 200). The typed methods (`*Typed`) were never affected. Internally switched from `.body(String.class)` to `.toEntity(String.class)`.
- **`ApiLogService.saveApiCallError` now extracts HTTP status from Spring exceptions.** `status_code` was always NULL on ERROR / RETRY_ERROR rows. Now lifted off `HttpStatusCodeException` / `RestClientResponseException`.
- **`error_message` JSONB column now matches the documented shape.** Previously stored as `toJsonNode(message)` — just the raw exception message string — which contradicted docs that promised `{type, message}`. Now writes structured `{"type": "<fqcn>", "message": "<message>" [, "responseBody": "<upstream body>"]}`.

### Docs

- **`retry-handling` guide rewritten** to be accurate: `RestApiClientUtil` does NOT propagate retry context (each retry gets a new `request_id`); the supported path for retry-timeline tracking is manual event publishing. Added a section on `ApiEventListener`'s own `@Retryable` log-write retries.
- **`error_message` reference updated** to document the new `responseBody` field (only present for HTTP exceptions carrying a body).
- README schema columns corrected: `event_type VARCHAR(50)` (was 20), `request_id VARCHAR(36)` (was 255).
- Removed misleading "Production-tested" / "Spring Retry integration with RETRY_ERROR events" claims.
- Maven Central and CI badges added to READMEs.

### Migration from v0.3.0

Mostly backward-compatible — same API. Watch for:

- **`status_code` column on raw-method SUCCESS rows**: was always 200, now reflects reality (201, 204, etc.). Any query that hardcoded `status_code = 200` may need adjustment.
- **`error_message` JSONB shape**: was a raw message string or `{"raw": "..."}` fallback. Now structured `{type, message, responseBody?}`. Queries against `error_message ->> 'raw'` no longer match — use `error_message ->> 'message'`.
- **`status_code` column on ERROR rows**: was always NULL. Now lifted from Spring exceptions when applicable (NULL for non-HTTP exceptions like timeouts).

## [0.3.0] — BUILTIN schema management is the new default

### Changed

- **BUILTIN is now the default schema management strategy.** v0.2.0 made schema management opt-in with `NONE` as the default, but that left users without Flyway/Liquibase staring at a "table doesn't exist" error on first boot. v0.3.0 flips the default to `BUILTIN` — the starter runs `CREATE TABLE IF NOT EXISTS` automatically via Spring Boot's `DataSourceScriptDatabaseInitializer`, so the table just exists.
- `V1.0__create_api_log.sql` now uses `IF NOT EXISTS` clauses, making it idempotent and safe to re-run on every boot. Flyway is fine with this; the version row in `flyway_schema_history` is what matters, not whether the SQL did anything.

### Strategy summary (after v0.3.0)

- `api.log.schema.management=builtin` (default) — starter creates the table on startup
- `api.log.schema.management=flyway` — starter registers a `FlywayConfigurationCustomizer` (requires Flyway on classpath)
- `api.log.schema.management=none` — starter does not touch the schema; you apply the DDL yourself

### Migration from v0.2.0

- **You had `management=flyway` set explicitly:** no change needed.
- **You had `management=none` set explicitly:** no change needed.
- **You did not set `management` (relying on the v0.2.0 default `NONE` and applying the DDL elsewhere):** either set `management=none` explicitly to preserve old behavior, or let BUILTIN take over (it's idempotent, so it won't fight your existing table).

## [0.2.0] — Schema management opt-in

### Changed

- **BREAKING: schema management is now opt-in.** v0.1.0 force-installed Flyway and auto-ran the bundled migration. v0.2.0 makes the consumer choose:
    - `api.log.schema.management=none` (default) — apply the DDL yourself (Liquibase, manual `psql`, your own Flyway flow)
    - `api.log.schema.management=flyway` — starter registers a `FlywayConfigurationCustomizer` that adds its location to your Flyway setup
- `flyway-core` and `flyway-database-postgresql` are now `<optional>true</optional>` — consumers who want `management=flyway` must declare Flyway in their own build.
- Migration relocated from `classpath:db/migration/V1.0__create_api_log.sql` to `classpath:db/api-log/V1.0__create_api_log.sql` so it no longer collides with the consumer's default Flyway location.

### Migration guide (from v0.1.0)

If you were depending on v0.1.0's auto-migration:

1. Add `org.flywaydb:flyway-core` (and `flyway-database-postgresql` runtime) to your dependencies.
2. Set `api.log.schema.management=flyway` in your config.

If you'd rather apply the schema yourself (recommended for production):

1. Copy the SQL from [Schema reference](reference/schema.md) into your migration tool of choice.
2. Leave `api.log.schema.management` at its default (`none`).

## [0.1.0] — Initial release

First public release. Repackaged as a standalone Spring Boot starter.

### Changed

- **Restructured as standalone Spring Boot Starter library.**
    - `groupId`: `com.devs.lab` → `kr.devslab`
    - `artifactId`: `api-log-starter` → `api-log-spring-boot-starter`
    - Java package: `com.devs.lab.test.*` → `kr.devslab.apilog.*`
- Removed demo application (`ApiLogApplication`, `compose.yaml`, `Dockerfile.postgres`) — pure library.
- Removed app-only dependencies: `spring-boot-devtools`, `spring-boot-docker-compose`, `spring-boot-starter-actuator`.
- Removed `spring-boot-maven-plugin` from build (library, not executable jar).

### Fixed

- Flyway migration path: `db.migration/` → `db/migration/` (the dot version wasn't a standard Flyway location and never auto-applied).

### Added

- `LICENSE` (Apache 2.0) + `NOTICE`.
- Full `pom.xml` metadata (licenses, SCM, developers, organization) required for Maven Central publishing.
- Bilingual README (English default + `README.ko.md`).
- This documentation site.

### Highlights

- Async, event-driven API call logging via `ApplicationEventPublisher`.
- `RestApiClientUtil` bundled HTTP client with `GET` / `POST` × `sync` / `async` × `raw` / `typed`.
- PostgreSQL JSONB storage for request/response/error bodies.
- `RETRY_ERROR` event type for retry attempts (consumer publishes manually or via their own `@Retryable` wrapper).
- Auto-configuration via `ApiLogAutoConfiguration` with `@ConditionalOnMissingBean` overrides.
- comprehensive test suite covering services, repository, listener, and Testcontainers-backed PostgreSQL integration.

[Unreleased]: https://github.com/devslab-kr/api-log/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.5.0
[0.4.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.4.0
[0.3.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.3.0
[0.2.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.2.0
[0.1.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.1.0
