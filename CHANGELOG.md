# Changelog

The full changelog with anchored links is published at
**<https://api-log.devslab.kr/changelog/>** ([한국어](https://api-log.devslab.kr/ko/changelog/)).

The source of truth for the entries below is [docs/changelog.md](docs/changelog.md) — please update there.

## [Unreleased]

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

[Unreleased]: https://github.com/devslab-kr/api-log/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.2.0
[0.1.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.1.0
