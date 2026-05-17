# 변경 이력

`api-log-spring-boot-starter`의 모든 주요 변경 사항을 기록합니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며, 본 프로젝트는 [Semantic Versioning](https://semver.org/lang/ko/)을 준수합니다.

## [Unreleased]

## [0.2.0] — 스키마 관리 옵트인

### Changed

- **BREAKING: 스키마 관리가 옵트인 방식으로 변경.** v0.1.0은 Flyway를 강제로 깔고 번들 마이그레이션을 자동 실행했습니다. v0.2.0부터는 사용자가 선택:
    - `api.log.schema.management=none` (기본) — DDL 직접 적용 (Liquibase, 수동 `psql`, 자체 Flyway 흐름)
    - `api.log.schema.management=flyway` — 스타터가 `FlywayConfigurationCustomizer`를 등록해 자기 마이그레이션 위치를 Flyway에 추가
- `flyway-core`, `flyway-database-postgresql`이 `<optional>true</optional>`로 변경 — `management=flyway` 쓰는 사용자가 직접 Flyway 의존성 추가 필요.
- 마이그레이션 위치 이동: `classpath:db/migration/V1.0__create_api_log.sql` → `classpath:db/api-log/V1.0__create_api_log.sql` (사용자 기본 Flyway 위치와 충돌 방지).

### v0.1.0에서 마이그레이션

v0.1.0의 자동 마이그레이션에 의존하고 있었다면:

1. `org.flywaydb:flyway-core` (+ runtime의 `flyway-database-postgresql`)를 본인 의존성에 추가.
2. 설정에 `api.log.schema.management=flyway` 추가.

스키마를 직접 적용하고 싶다면 (운영 환경 권장):

1. [스키마 레퍼런스](reference/schema.md)의 SQL을 본인 마이그레이션 도구에 복사.
2. `api.log.schema.management`는 기본값(`none`) 그대로.

## [0.1.0] — 첫 공개

첫 공개 릴리스. 독립 Spring Boot starter로 재패키징.

### Changed

- **독립 Spring Boot Starter 라이브러리로 재구성.**
    - `groupId`: `com.devs.lab` → `kr.devslab`
    - `artifactId`: `api-log-starter` → `api-log-spring-boot-starter`
    - Java 패키지: `com.devs.lab.test.*` → `kr.devslab.apilog.*`
- 데모 애플리케이션 제거 (`ApiLogApplication`, `compose.yaml`, `Dockerfile.postgres`) — 순수 라이브러리.
- 앱 전용 의존성 제거: `spring-boot-devtools`, `spring-boot-docker-compose`, `spring-boot-starter-actuator`.
- 빌드에서 `spring-boot-maven-plugin` 제거 (라이브러리, 실행 jar 아님).

### Fixed

- Flyway 마이그레이션 경로: `db.migration/` → `db/migration/` (점 버전은 표준 Flyway 위치가 아니라 자동 적용되지 않았음).

### Added

- `LICENSE` (Apache 2.0) + `NOTICE`.
- Maven Central 발행에 필요한 `pom.xml` 메타데이터 (licenses, SCM, developers, organization).
- 양국어 README (영문 기본 + `README.ko.md`).
- 본 문서 사이트.

### 하이라이트

- `ApplicationEventPublisher`를 통한 비동기, 이벤트 드리븐 API 호출 로깅.
- `RestApiClientUtil` 내장 HTTP 클라이언트 — `GET` / `POST` × `sync` / `async` × `raw` / `typed`.
- 요청/응답/에러 본문의 PostgreSQL JSONB 저장.
- Spring Retry 통합과 `RETRY_ERROR` 이벤트.
- `ApiLogAutoConfiguration`을 통한 자동 구성, `@ConditionalOnMissingBean` 오버라이드.
- Testcontainers 기반 PostgreSQL 통합 테스트 31개.

[Unreleased]: https://github.com/devslab-kr/api-log/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.2.0
[0.1.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.1.0
