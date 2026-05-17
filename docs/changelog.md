# Changelog

All notable changes to `api-log-spring-boot-starter` are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- **Restructured as standalone Spring Boot Starter library.**
    - `groupId`: `com.devs.lab` → `kr.devslab`
    - `artifactId`: `api-log-starter` → `api-log-spring-boot-starter`
    - Java package: `com.devs.lab.test.*` → `kr.devslab.apilog.*`
- Removed demo application (`ApiLogApplication`, `compose.yaml`, `Dockerfile.postgres`) — pure library now
- Removed app-only dependencies: `spring-boot-devtools`, `spring-boot-docker-compose`, `spring-boot-starter-actuator`
- Removed `spring-boot-maven-plugin` from build (library, not executable jar)

### Fixed

- Flyway migration path: `db.migration/` → `db/migration/` (the dot version wasn't a standard Flyway location and never auto-applied)

### Added

- `LICENSE` (Apache 2.0) + `NOTICE`
- Full `pom.xml` metadata (licenses, SCM, developers, organization) required for Maven Central publishing
- Bilingual README (English default + `README.ko.md`)
- This documentation site

## [0.1.0] — Initial release

First public release. Repackaged as a standalone Spring Boot starter.

### Highlights

- Async, event-driven API call logging via `ApplicationEventPublisher`
- `RestApiClientUtil` bundled HTTP client with `GET` / `POST` × `sync` / `async` × `raw` / `typed`
- PostgreSQL JSONB storage for request/response/error bodies
- Spring Retry integration with `RETRY_ERROR` events
- Auto-configuration via `ApiLogAutoConfiguration` with `@ConditionalOnMissingBean` overrides
- 31 tests including PostgreSQL integration via Testcontainers

[Unreleased]: https://github.com/devslab-kr/api-log/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.1.0
