# 변경 이력

`api-log-spring-boot-starter`의 모든 주요 변경 사항을 기록합니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며, 본 프로젝트는 [Semantic Versioning](https://semver.org/lang/ko/)을 준수합니다.

## [Unreleased]

### Changed

- **독립 Spring Boot Starter 라이브러리로 재구성.**
    - `groupId`: `com.devs.lab` → `kr.devslab`
    - `artifactId`: `api-log-starter` → `api-log-spring-boot-starter`
    - Java 패키지: `com.devs.lab.test.*` → `kr.devslab.apilog.*`
- 데모 애플리케이션 제거 (`ApiLogApplication`, `compose.yaml`, `Dockerfile.postgres`) — 순수 라이브러리
- 앱 전용 의존성 제거: `spring-boot-devtools`, `spring-boot-docker-compose`, `spring-boot-starter-actuator`
- 빌드에서 `spring-boot-maven-plugin` 제거 (라이브러리, 실행 jar 아님)

### Fixed

- Flyway 마이그레이션 경로: `db.migration/` → `db/migration/` (점 버전은 표준 Flyway 위치가 아니라 자동 적용되지 않았음)

### Added

- `LICENSE` (Apache 2.0) + `NOTICE`
- Maven Central 발행에 필요한 `pom.xml` 메타데이터 (licenses, SCM, developers, organization)
- 양국어 README (영문 기본 + `README.ko.md`)
- 본 문서 사이트

## [0.1.0] — 첫 공개

첫 공개 릴리스. 독립 Spring Boot starter로 재패키징.

### 하이라이트

- `ApplicationEventPublisher`를 통한 비동기, 이벤트 드리븐 API 호출 로깅
- `RestApiClientUtil` 내장 HTTP 클라이언트 — `GET` / `POST` × `sync` / `async` × `raw` / `typed`
- 요청/응답/에러 본문의 PostgreSQL JSONB 저장
- Spring Retry 통합과 `RETRY_ERROR` 이벤트
- `ApiLogAutoConfiguration`을 통한 자동 구성, `@ConditionalOnMissingBean` 오버라이드
- Testcontainers 기반 PostgreSQL 통합 테스트 31개

[Unreleased]: https://github.com/devslab-kr/api-log/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.1.0
