# Changelog

The full changelog with anchored links is published at
**<https://api-log.devslab.kr/changelog/>** ([한국어](https://api-log.devslab.kr/ko/changelog/)).

The source of truth for the entries below is [docs/changelog.md](docs/changelog.md) — please update there.

## [Unreleased]

## [3.0.1] — HTTP client fixes: Content-Type on body + PATCH method support

Two bugs in the HTTP client utilities (`RestApiClientUtil` /
`ReactiveApiClientUtil`) surfaced when the first real downstream consumer
(`devslab-examples`'s `api-log-*-demo` set) exercised the POST/PUT/PATCH paths
through actual `@RequestBody`-annotated Spring controllers:

### Fixed

- **`Content-Type` header missing on POST/PUT/PATCH bodies.** The utils
  serialised the body via Jackson then passed the resulting string to
  `RestClient.body(String)` / `WebClient.bodyValue(String)` — which routes
  through Spring's `StringHttpMessageConverter` and writes
  `Content-Type: text/plain;charset=ISO-8859-1` by default. Downstream services
  binding with `@RequestBody Foo` then rejected the call as Unsupported Media
  Type. Fix sets `application/json` explicitly in both `exchange()` paths.
- **`patchSync*` / `patchAsync*` was broken end-to-end.** The autoconfig used
  `SimpleClientHttpRequestFactory` (backed by `java.net.HttpURLConnection`),
  whose `setRequestMethod` throws `ProtocolException: Invalid HTTP method:
  PATCH` — a long-standing JDK limitation. Swapped to
  `JdkClientHttpRequestFactory` (backed by `java.net.http.HttpClient`, Java
  11+) which supports all five verbs natively. Read-timeout property
  preserved.

### Added

- **End-to-end integration test coverage** for both HTTP client utils
  (`core/src/test/.../util/`):
    - `RestApiClientUtilWireIT` / `ReactiveApiClientUtilWireIT` —
      MockWebServer-driven wire-level assertions: `Content-Type` on every
      body-carrying verb, exact body bytes, UTF-8 encoding (Korean + emoji),
      large bodies (32 KB), HTTP method propagation, no leakage of internal
      `requestId` field into wire headers.
    - `RestApiClientUtilSpringE2EIT` / `ReactiveApiClientUtilSpringE2EIT` —
      `@SpringBootTest` with real `@RestController`s on `@RequestBody Foo`,
      verifies round-trip serialisation through Tomcat / reactor-netty. The
      Servlet IT and the WebFlux IT each pin `spring.main.web-application-type`
      because the test classpath has both starters.

Together: 65 new test cases. The existing `RestApiClientUtilRoutingTest` /
`ReactiveApiClientUtilRoutingTest` (subclass-based, no real HTTP) didn't catch
either bug because they never reached the network layer.

### Compatibility

- **No API changes.** All `RestApiClientUtil` / `ReactiveApiClientUtil` method
  signatures unchanged. Strict drop-in upgrade from `3.0.0`.
- **Behaviour change for callers who pass a non-JSON String body.** Before
  3.0.1, raw String payloads went out as `text/plain`. After 3.0.1, all
  body-carrying calls send `application/json`. If you genuinely need a
  different content type for an outbound call, use Spring's `RestClient` /
  `WebClient` directly — api-log's wrappers are explicitly JSON-only by design
  (the whole library is JSON+JSONB-centric).
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

Recommended for everyone on `3.0.0` — any consumer that ever calls a
body-carrying method against a real Spring controller is affected.

## [3.0.0] — Spring-major-aligned versioning policy

**Renumbering of `0.6.0`** per the new [Spring-major-aligned versioning policy](https://github.com/devslab-kr/.github/blob/main/.github/VERSIONING.md). No API, behaviour, or dependency changes — the major number is bumped from `0.6` to `3.0` to match the Spring Boot major this line targets (Spring Boot 3). The published JAR bytes are identical to `0.6.0` apart from the version coordinate in the POM.

Going forward, all Spring Boot 3 releases of api-log ship on the `3.x.y` line. When a Spring Boot 4 line ships, it will be `4.x.y`. The previous `0.6.0` artifacts remain on Maven Central as historical references.

### Upgrading from `0.6.0`

```diff
- implementation("kr.devslab:api-log-jpa:0.6.0")
+ implementation("kr.devslab:api-log-jpa:3.0.0")
- implementation("kr.devslab:api-log-r2dbc:0.6.0")
+ implementation("kr.devslab:api-log-r2dbc:3.0.0")
- implementation("kr.devslab:api-log-mybatis:0.6.0")
+ implementation("kr.devslab:api-log-mybatis:3.0.0")
```

No other changes. Same Spring Boot 3 baseline, same `ApiLogWriter` SPI, same auto-configuration.

## [0.6.0] — Multi-module split (Gradle), pluggable JPA / R2DBC / MyBatis backends

### Changed

- **The single `api-log-spring-boot-starter` artifact is split.** Consumers now add `kr.devslab:api-log-core` plus exactly one backend artifact: `api-log-jpa` (drop-in for v0.5.x), `api-log-r2dbc` (reactive), or `api-log-mybatis`.
- **Build system: Maven → Gradle 8.10** with Vanniktech maven-publish per module.
- **Package renames**: `model.dto` → `dto`, `model.ApiLogEntity` → `jpa.model.ApiLogEntity`, `service.ApiLogService` → `jpa.writer.JpaApiLogWriter`. Full mapping in [docs/changelog.md](docs/changelog.md#060--multi-module-split-gradle-pluggable-jpa--r2dbc--mybatis-backends).

### Added

- **`ApiLogWriter` SPI** — backend-agnostic three-method interface (`writeInitiated` / `writeSuccess` / `writeError`). Each backend artifact registers one implementation; the core listener routes events through it.
- **`api-log-r2dbc`** — reactive backend using R2DBC's `DatabaseClient`. Pure-reactive schema initializer; no JDBC pull-in.
- **`api-log-mybatis`** — MyBatis mapper backend with `::jsonb` cast on inserts.

### Fixed

- `V1.0__create_api_log.sql` now uses `IF NOT EXISTS` on both CREATE TABLE and CREATE INDEX — idempotent across boots under BUILTIN mode.

Full migration notes in [docs/changelog.md](docs/changelog.md#060--multi-module-split-gradle-pluggable-jpa--r2dbc--mybatis-backends).

## [0.5.2] — Fix bean registration in real consumer apps

### Fixed

- `RestApiClientUtil` + four `@Configuration` classes were never registered in consumer apps (relied on `@ComponentScan` reaching the starter's package). Fixed by splitting into three `@AutoConfiguration` classes registered via `META-INF/spring/.../AutoConfiguration.imports`.
- `spring-boot-starter-web` is now `<optional>true</optional>` — pure-WebFlux apps no longer get a Servlet stack forced onto their classpath.

Full notes in [docs/changelog.md](docs/changelog.md#052--fix-bean-registration-in-real-consumer-apps).

## [0.5.1] — Reactive (WebFlux) client + end-to-end HTTP tests

### Added

- **`ReactiveApiClientUtil`** — `WebClient`-backed reactive client, returns `Mono<ApiResponse>` / `Mono<T>`. Same API shape as `RestApiClientUtil`. Auto-registered when `spring-webflux` is on the classpath (declared optional).
- MockWebServer + Testcontainers HTTP integration tests for both clients — real HTTP, real DB, real assertions on `api_log`.

### Fixed

- `ApiLogService` now extracts `status_code` / `responseBody` from `WebClientResponseException` (parallel hierarchy to `HttpStatusCodeException`) via reflection. Reactive 4xx/5xx rows previously had `status_code = NULL`.

Full notes in [docs/changelog.md](docs/changelog.md#051--reactive-webflux-client--end-to-end-http-tests).

## [0.5.0] — PUT / DELETE / PATCH + retry-correlation via core API

### Added

- **PUT / DELETE / PATCH** convenience methods on `RestApiClientUtil` (12 new methods).
- **Core `send` / `sendAsync` / `sendTyped` / `sendAsyncTyped`** API taking `(HttpMethod, ApiRequest)` directly. Lets callers supply an explicit `requestId` so retry attempts share a correlation key.

### Changed

- Internal refactor: all 22 convenience methods on `RestApiClientUtil` now funnel through the four core `send*` methods. Public API unchanged.

Fully backward-compatible with v0.4.0.

## [0.4.0] — Bug fixes: real status codes, structured errors, honest retry docs

### Fixed

- `RestApiClientUtil` raw methods: `status_code` was hardcoded to 200; now reflects the actual HTTP response status.
- `ApiLogService.saveApiCallError`: `status_code` on ERROR/RETRY_ERROR rows was always NULL; now lifted from Spring `HttpStatusCodeException` / `RestClientResponseException`.
- `error_message` JSONB column: was a raw message string; now structured `{type, message, responseBody?}` matching the docs.

### Docs

- Retry-handling guide rewritten to be accurate (RestApiClientUtil doesn't propagate retry context — supported path is manual event publishing).
- Schema column types corrected in READMEs (VARCHAR(50)/(36), not VARCHAR(20)/(255)).
- "Production-tested" claim removed; "Spring Retry integration with RETRY_ERROR events" rephrased — that claim was specifically wrong (Spring Retry applies to the listener's DB writes, not HTTP calls).
- Maven Central + CI badges added to READMEs.

Full migration notes from v0.3.0 in [docs/changelog.md](docs/changelog.md#040--bug-fixes-real-status-codes-structured-errors-honest-retry-docs).

## [0.3.0] — BUILTIN schema management is the new default

### Changed

- **`BUILTIN` is now the default for `api.log.schema.management`.** The starter runs `CREATE TABLE IF NOT EXISTS` on startup via Spring Boot's `DataSourceScriptDatabaseInitializer` — no migration tool needed. Flips v0.2.0's `NONE` default which left first-time users with a missing table.
- `V1.0__create_api_log.sql` now uses `IF NOT EXISTS` clauses, idempotent and safe to re-run on every boot.

Three strategies now: `builtin` (default), `flyway`, `none`. See [docs/changelog.md](docs/changelog.md#030--builtin-schema-management-is-the-new-default) for the full v0.2.0 migration guide.

## [0.2.0] — Schema management opt-in

### Changed

- **BREAKING: schema management is now opt-in.** v0.1.0 force-installed Flyway and auto-ran the bundled migration. v0.2.0 makes the consumer choose:
    - `api.log.schema.management=none` (default) — apply the DDL yourself
    - `api.log.schema.management=flyway` — starter customizes Flyway to include its location
- `flyway-core` / `flyway-database-postgresql` now `<optional>true</optional>` — consumers using `management=flyway` must declare Flyway themselves.
- Migration moved: `classpath:db/migration/` → `classpath:db/api-log/` (avoids collision with consumer's default Flyway location).

See [docs/changelog.md](docs/changelog.md#020--schema-management-opt-in) for the full migration guide from v0.1.0.

## [0.1.0] — Initial release

First public release. See [docs/changelog.md](docs/changelog.md#010--initial-release) for details.

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
