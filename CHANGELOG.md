# Changelog

The full changelog with anchored links is published at
**<https://api-log.devslab.kr/changelog/>** ([한국어](https://api-log.devslab.kr/ko/changelog/)).

The source of truth for the entries below is [docs/changelog.md](docs/changelog.md) — please update there.

## [Unreleased]

### Changed

- **Restructured as standalone Spring Boot Starter library.**
    - `groupId`: `com.devs.lab` → `kr.devslab`
    - `artifactId`: `api-log-starter` → `api-log-spring-boot-starter`
    - Java package: `com.devs.lab.test.*` → `kr.devslab.apilog.*`
- Removed demo application (`ApiLogApplication`, `compose.yaml`, `Dockerfile.postgres`) — pure library now
- Removed app-only dependencies: `spring-boot-devtools`, `spring-boot-docker-compose`, `spring-boot-starter-actuator`

### Fixed

- Flyway migration path: `db.migration/` → `db/migration/` (the dot version wasn't a standard Flyway location and never auto-applied)

### Added

- `LICENSE` (Apache 2.0) + `NOTICE`
- Full `pom.xml` metadata for Maven Central publishing
- Bilingual README (English default + `README.ko.md`)
- Full documentation site at [api-log.devslab.kr](https://api-log.devslab.kr)

## [0.1.0] — Initial release

First public release. See [docs/changelog.md](docs/changelog.md#010--initial-release) for details.

[Unreleased]: https://github.com/devslab-kr/api-log/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.1.0
