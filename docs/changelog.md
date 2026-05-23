# Changelog

All notable changes to `api-log-spring-boot-starter` are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [3.0.1] — HTTP client fixes: Content-Type on body + PATCH method support

Two bugs in the HTTP client utilities (`RestApiClientUtil` /
`ReactiveApiClientUtil`) surfaced when the first real downstream consumer
(`devslab-examples`'s `api-log-*-demo` set) exercised the POST/PUT/PATCH paths
through actual `@RequestBody`-annotated Spring controllers.

### Fixed

- **`Content-Type` header was missing on POST/PUT/PATCH bodies.** The utils
  serialised the body via Jackson then passed the resulting `String` to
  `RestClient.body(String)` / `WebClient.bodyValue(String)`. Spring's
  `StringHttpMessageConverter` then wrote the body as
  `Content-Type: text/plain;charset=ISO-8859-1` (its default for raw String
  bodies), and downstream services that bind with `@RequestBody Foo` rejected
  it as Unsupported Media Type. The fix sets `application/json` explicitly in
  both `exchange()` methods.
- **`patchSync*` / `patchAsync*` was broken end-to-end.** The auto-config
  registered a `SimpleClientHttpRequestFactory` (backed by
  `java.net.HttpURLConnection`), whose `setRequestMethod` throws
  `ProtocolException: Invalid HTTP method: PATCH` — a long-standing JDK
  limitation. Swapped to `JdkClientHttpRequestFactory` (backed by
  `java.net.http.HttpClient`, Java 11+) which supports all five HTTP verbs.
  Read-timeout property preserved; connect-timeout default is left to
  `HttpClient`'s built-in.

### Added — End-to-end integration test coverage

`core/src/test/java/.../util/` now has four new test classes (65 cases
total):

- **`RestApiClientUtilWireIT`** / **`ReactiveApiClientUtilWireIT`** —
  MockWebServer-driven wire-level assertions. Verify `Content-Type` on every
  body-carrying verb, exact body bytes, UTF-8 encoding (Korean + emoji round
  trip), large bodies (32 KB), HTTP method propagation, and that internal
  fields (e.g. `ApiRequest.requestId`) don't leak into wire headers.
- **`RestApiClientUtilSpringE2EIT`** / **`ReactiveApiClientUtilSpringE2EIT`** —
  Real `@SpringBootTest` with `@RestController` declaring `@RequestBody Foo`
  handlers, on Tomcat / reactor-netty respectively. Each pins
  `spring.main.web-application-type` because the test classpath has both
  starters. Covers all five verbs + 4xx/5xx propagation + Unicode/nested/
  null-field round-trips.

The existing `RestApiClientUtilRoutingTest` /
`ReactiveApiClientUtilRoutingTest` (subclass-based, no real HTTP) couldn't
catch either of these bugs because they never reached the network layer. The
new IT classes close that gap and act as regression coverage for any future
HTTP-client refactor.

### Compatibility

- **No API changes.** All `RestApiClientUtil` / `ReactiveApiClientUtil` method
  signatures unchanged. Strict drop-in upgrade from `3.0.0`.
- **Behaviour change for raw non-JSON String bodies.** Before `3.0.1`, raw
  String payloads went out as `text/plain`. After `3.0.1`, all body-carrying
  calls send `application/json`. If you genuinely need a different content
  type for an outbound call, use Spring's `RestClient` / `WebClient`
  directly — api-log's wrappers are explicitly JSON-only by design (the whole
  library is JSON + JSONB-centric).
- **`ClientHttpRequestFactory` bean swap.** Any consumer that supplied their
  own `ClientHttpRequestFactory` via `@ConditionalOnMissingBean` continues to
  win; only the default factory changed.

### Upgrading from `3.0.0`

```diff
- implementation("kr.devslab:api-log-core:3.0.0")
+ implementation("kr.devslab:api-log-core:3.0.1")
- implementation("kr.devslab:api-log-jpa:3.0.0")
+ implementation("kr.devslab:api-log-jpa:3.0.1")
- implementation("kr.devslab:api-log-r2dbc:3.0.0")
+ implementation("kr.devslab:api-log-r2dbc:3.0.1")
- implementation("kr.devslab:api-log-mybatis:3.0.0")
+ implementation("kr.devslab:api-log-mybatis:3.0.1")
```

Recommended for everyone on `3.0.0` — any consumer that calls a body-carrying
method against a real Spring controller is affected.

## [3.0.0] — Spring-major-aligned versioning policy

**Renumbering of `0.6.0`** per the new [Spring-major-aligned versioning policy](https://github.com/devslab-kr/.github/blob/main/.github/VERSIONING.md). No API, behaviour, or dependency changes — the major number bumps from `0.6` to `3.0` to match the Spring Boot major this line targets (Spring Boot 3). Published JAR bytes are identical to `0.6.0` apart from the version coordinate.

Going forward, all Spring Boot 3 releases of api-log ship on the `3.x.y` line. The previous `0.6.0` artifacts remain on Maven Central as historical references.

### Upgrading from `0.6.0`

```diff
- implementation("kr.devslab:api-log-jpa:0.6.0")
+ implementation("kr.devslab:api-log-jpa:3.0.0")
- implementation("kr.devslab:api-log-r2dbc:0.6.0")
+ implementation("kr.devslab:api-log-r2dbc:3.0.0")
- implementation("kr.devslab:api-log-mybatis:0.6.0")
+ implementation("kr.devslab:api-log-mybatis:3.0.0")
```

No other changes. Same `ApiLogWriter` SPI, same auto-configuration shape.

## [0.6.0] — Multi-module split (Gradle), pluggable JPA / R2DBC / MyBatis backends

### Changed

- **The single `api-log-spring-boot-starter` artifact is gone.** The starter is now a multi-module Gradle build. Consumers add `api-log-core` plus exactly one backend artifact:

  | Artifact (Maven coordinate) | What it provides |
  | --- | --- |
  | `kr.devslab:api-log-core` | Events, SPI, async listener, HTTP client utilities |
  | `kr.devslab:api-log-jpa` | JPA + Hibernate persistence (the v0.5.x behavior) |
  | `kr.devslab:api-log-r2dbc` | Reactive R2DBC persistence — no JDBC dependency |
  | `kr.devslab:api-log-mybatis` | MyBatis mapper persistence |

  Adding `api-log-jpa` is the closest drop-in for v0.5.x users; it pulls `api-log-core` transitively.

- **Build system: Maven → Gradle 8.10.** Adopting easy-paging's convention: Vanniktech maven-publish per module, configuration cache disabled in CI to play nice with the publishing plugin. The Maven build files are gone — `./gradlew build` is the only path now.

- **Package renames** to reflect the layout:
  - `kr.devslab.apilog.model.dto.ApiRequest` → `kr.devslab.apilog.dto.ApiRequest`
  - `kr.devslab.apilog.model.dto.ApiResponse` → `kr.devslab.apilog.dto.ApiResponse`
  - `kr.devslab.apilog.model.ApiLogEntity` → `kr.devslab.apilog.jpa.model.ApiLogEntity` (now lives in `api-log-jpa`)
  - `kr.devslab.apilog.repository.ApiLogRepository` → `kr.devslab.apilog.jpa.repository.ApiLogRepository`
  - `kr.devslab.apilog.service.ApiLogService` → replaced by `kr.devslab.apilog.jpa.writer.JpaApiLogWriter` (implements the new `ApiLogWriter` SPI)

### Added

- **`ApiLogWriter` SPI** (`kr.devslab.apilog.spi.ApiLogWriter`) — three-method interface that every backend implements (`writeInitiated`, `writeSuccess`, `writeError`). The core listener routes events through whatever writer bean the consumer's backend artifact registered.
- **`api-log-r2dbc`** — reactive backend that talks to PostgreSQL via R2DBC's `DatabaseClient`. JSONB binding uses the R2DBC PostgreSQL driver's implicit `TEXT → JSONB` cast, no manual `::jsonb` needed. Ships a pure-reactive schema initializer (`R2dbcScriptDatabaseInitializer`) — zero JDBC pull-in.
- **`api-log-mybatis`** — MyBatis backend with a `@Mapper`-annotated interface. JSONB columns use `CAST(#{...,jdbcType=VARCHAR} AS jsonb)` in the `@Insert` SQL so no custom `TypeHandler` is required.
- **Shared SPI helpers in `:core`**:
  - `HttpErrorExtractor` — pulls HTTP status + body off thrown exceptions (was inline in the old `ApiLogService`).
  - `PayloadJsonMapper` — JSON string / `JsonNode` conversion used by every writer.

### Fixed

- **`V1.0__create_api_log.sql` is now idempotent.** Both `CREATE TABLE` and `CREATE INDEX` got `IF NOT EXISTS`. Previously the second boot under BUILTIN mode could fail with "relation already exists" if Hibernate's `ddl-auto` wasn't catching it.

### Migration from v0.5.2

Update your dependency coordinates:

```xml
<!-- v0.5.x -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>api-log-spring-boot-starter</artifactId>
    <version>0.5.2</version>
</dependency>

<!-- v0.6.0 — JPA backend (drop-in for existing setups) -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>api-log-jpa</artifactId>
    <version>0.6.0</version>
</dependency>
```

If you import any of the moved types directly, update the package:

```java
// Before
import kr.devslab.apilog.model.dto.ApiRequest;
import kr.devslab.apilog.model.ApiLogEntity;

// After
import kr.devslab.apilog.dto.ApiRequest;
import kr.devslab.apilog.jpa.model.ApiLogEntity;
```

Reactive (R2DBC) or MyBatis adopters: swap `api-log-jpa` for `api-log-r2dbc` / `api-log-mybatis` instead — same `ApiLogWriter` contract, same `api_log` table.

## [0.5.2] — Fix bean registration in real consumer apps

### Fixed

- **`RestApiClientUtil`, `AsyncConfig`, `JacksonConfig`, `RestClientConfig` were never registered in consumer apps.** They relied on the consumer's `@ComponentScan` reaching the `kr.devslab.apilog` package, which it doesn't for apps with a different base package. The starter's tests passed only because the test's `TestApp` sat at the package root and scanned everything. In real usage:
    - `@Autowired RestApiClientUtil` would throw `NoSuchBeanDefinitionException`
    - The Blackbird-equipped `ObjectMapper` was missing — Spring Boot's default was used
    - The custom async executor was missing — Spring Boot's default was used
    - `RestClient` with the configured timeouts and message converters was missing
- Fix: split `ApiLogAutoConfiguration` into three `@AutoConfiguration` classes, register all in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Spring Boot 3 discovery):
    - `ApiLogAutoConfiguration` — core (event listener, service, schema initializer, async executor, `ObjectMapper` with Blackbird, retry config)
    - `RestApiClientAutoConfiguration` — blocking HTTP, gated by `@ConditionalOnClass(RestClient.class)` (registers `RestClient`, `MappingJackson2HttpMessageConverter`, `ClientHttpRequestFactory`, `RestApiClientUtil`)
    - `ReactiveApiClientAutoConfiguration` — reactive HTTP, gated by `@ConditionalOnClass(WebClient.class)` (registers `ReactiveApiClientUtil` from the auto-configured `WebClient.Builder`)
- `RestApiClientUtil` is no longer annotated `@Component`. The auto-config's `@Bean` (with `@ConditionalOnMissingBean`) registers it.

### Changed

- **`spring-boot-starter-web` is now `<optional>true</optional>`.** Pure-WebFlux apps no longer get a Servlet stack forced onto their classpath. Consumers who want the blocking `RestApiClientUtil` add `spring-boot-starter-web` (or just `spring-web`) themselves — most Servlet apps already have it.
- Same pattern Spring's own optional integrations follow, and matches easy-paging-spring-boot-starter's compileOnly approach.

### Removed (internal cleanup)

- The unused `RestTemplate` bean that `RestClientConfig` accidentally exposed. Use `RestClient` (Spring 6+ recommended) or declare your own.
- Stand-alone `AsyncConfig.java` / `JacksonConfig.java` / `RestClientConfig.java` files. Their content moved into the auto-configurations.

### Migration from v0.5.1

- If you depended on the (broken) `@ComponentScan` working — you weren't really getting `RestApiClientUtil`. Now it'll appear once your build resolves successfully.
- If you have a pure WebFlux app and weren't using the blocking client, the optional `spring-boot-starter-web` change means Tomcat etc. no longer ship transitively. Cleaner.
- If you were quietly relying on the `RestTemplate` bean — declare your own; it's no longer registered.

## [0.5.1] — Reactive (WebFlux) client + end-to-end HTTP tests

### Added

- **`ReactiveApiClientUtil`** — `WebClient`-backed reactive client. Same method surface as `RestApiClientUtil` (all 5 verbs × raw / typed + `send()` / `sendTyped()` cores) but returns `Mono<ApiResponse>` / `Mono<T>`. Same event-publishing contract, same `api_log` rows. Auto-registered via `ReactiveApiClientConfig` when `spring-webflux` is on the classpath. See the new [Reactive guide](guides/reactive.md).
- **`RestApiClientUtilHttpIntegrationTest`** and **`ReactiveApiClientUtilHttpIntegrationTest`** — end-to-end coverage using `MockWebServer` + Testcontainers PostgreSQL. Real HTTP traffic through both clients, real DB inserts via the async listener, then assertions on the `api_log` rows. Closes the gap where v0.4.0's hardcoded-status / unstructured-error_message bugs could ship undetected.
- **`ReactiveApiClientUtilRoutingTest`** — fast mock-based unit tests for reactive verb routing.

### Fixed

- `ApiLogService.saveApiCallError` now extracts `status_code` and `responseBody` from Spring WebFlux's `WebClientResponseException` (a separate class hierarchy from `HttpStatusCodeException`). Previously reactive 4xx/5xx ERROR rows had `status_code = NULL`. Done via reflective duck-typing so consumers who don't pull in `spring-webflux` aren't affected.

### Dependency notes

- `spring-webflux` and `reactor-netty-http` declared `<optional>true</optional>` — consumers who don't want the reactive client pay nothing.
- Test scope: `com.squareup.okhttp3:mockwebserver` 4.12.0, `io.projectreactor:reactor-test`.

### Migration from v0.5.0

Backward-compatible — all v0.5.0 APIs preserved. To use the reactive client, add `spring-webflux` + `reactor-netty-http` to your dependencies.

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

[Unreleased]: https://github.com/devslab-kr/api-log/compare/v3.0.1...HEAD
[3.0.1]: https://github.com/devslab-kr/api-log/releases/tag/v3.0.1
[3.0.0]: https://github.com/devslab-kr/api-log/releases/tag/v3.0.0
[0.6.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.6.0
[0.5.2]: https://github.com/devslab-kr/api-log/releases/tag/v0.5.2
[0.5.1]: https://github.com/devslab-kr/api-log/releases/tag/v0.5.1
[0.5.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.5.0
[0.4.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.4.0
[0.3.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.3.0
[0.2.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.2.0
[0.1.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.1.0
