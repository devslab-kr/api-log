# 변경 이력

`api-log-spring-boot-starter`의 모든 주요 변경 사항을 기록합니다.

형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며, 본 프로젝트는 [Semantic Versioning](https://semver.org/lang/ko/)을 준수합니다.

## [Unreleased]

## [0.5.2] — 실제 consumer 앱에서 빈 등록 문제 픽스

### Fixed

- **`RestApiClientUtil`, `AsyncConfig`, `JacksonConfig`, `RestClientConfig`가 실제 consumer 앱에서 등록되지 않던 버그.** Consumer의 `@ComponentScan`이 `kr.devslab.apilog` 패키지까지 닿아야 했는데, base package가 다른 앱들은 안 닿음. 스타터 테스트만 통과한 이유: `TestApp`이 패키지 루트에 있어서 전체 스캔. 실제 사용 시:
    - `@Autowired RestApiClientUtil` → `NoSuchBeanDefinitionException`
    - Blackbird `ObjectMapper`가 없었음 — Spring Boot 기본값 사용
    - 커스텀 async executor가 없었음 — Spring Boot 기본값 사용
    - 타임아웃·메시지 컨버터 설정된 `RestClient`가 없었음
- 픽스: `ApiLogAutoConfiguration`을 3개의 `@AutoConfiguration`으로 분리, `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Spring Boot 3 discovery)에 다 등록:
    - `ApiLogAutoConfiguration` — 코어 (이벤트 리스너, 서비스, 스키마 초기화, async executor, Blackbird `ObjectMapper`, retry config)
    - `RestApiClientAutoConfiguration` — 블로킹 HTTP, `@ConditionalOnClass(RestClient.class)` 게이트 (`RestClient`, `MappingJackson2HttpMessageConverter`, `ClientHttpRequestFactory`, `RestApiClientUtil` 등록)
    - `ReactiveApiClientAutoConfiguration` — 리액티브 HTTP, `@ConditionalOnClass(WebClient.class)` 게이트 (자동 구성된 `WebClient.Builder`에서 `ReactiveApiClientUtil` 등록)
- `RestApiClientUtil`에서 `@Component` 제거. Auto-config의 `@Bean` (`@ConditionalOnMissingBean`)이 등록.

### Changed

- **`spring-boot-starter-web`가 `<optional>true</optional>`로 변경.** 순수 WebFlux 앱에 Servlet 스택을 강제로 들이지 않음. 블로킹 `RestApiClientUtil`을 쓰는 사용자는 직접 `spring-boot-starter-web` (또는 `spring-web`) 추가 — 대부분의 Servlet 앱은 이미 갖고 있음.
- Spring 자체 옵셔널 통합과 같은 패턴이고, easy-paging-spring-boot-starter의 compileOnly 접근과 일치.

### Removed (내부 정리)

- `RestClientConfig`가 우연히 노출하던 미사용 `RestTemplate` 빈. `RestClient` (Spring 6+ 권장) 또는 본인이 직접 선언.
- 독립 `AsyncConfig.java` / `JacksonConfig.java` / `RestClientConfig.java` 파일. 내용이 auto-config들로 이동.

### v0.5.1에서 마이그레이션

- (망가진) `@ComponentScan`에 의존하고 있었다면 — 사실 `RestApiClientUtil`을 제대로 못 받고 있었던 것. 빌드 resolve되면 이제 정상 등장.
- 순수 WebFlux 앱이고 블로킹 클라이언트 안 썼다면, optional 변경으로 Tomcat 등이 더 이상 transitive로 안 옴. 깔끔.
- 조용히 `RestTemplate` 빈에 의존하고 있었다면 — 본인이 직접 선언; 더 이상 등록 안 됨.

## [0.5.1] — 리액티브 (WebFlux) 클라이언트 + end-to-end HTTP 테스트

### Added

- **`ReactiveApiClientUtil`** — `WebClient` 기반 리액티브 클라이언트. `RestApiClientUtil`과 동일한 메서드 표면 (5개 verb × raw / typed + `send()` / `sendTyped()` 코어)이지만 `Mono<ApiResponse>` / `Mono<T>` 반환. 동일한 이벤트 발행 계약, 동일한 `api_log` 행. `spring-webflux`가 클래스패스에 있을 때 `ReactiveApiClientConfig`가 자동 등록. 새 [리액티브 가이드](guides/reactive.md) 참고.
- **`RestApiClientUtilHttpIntegrationTest`** + **`ReactiveApiClientUtilHttpIntegrationTest`** — `MockWebServer` + Testcontainers PostgreSQL로 end-to-end 커버리지. 양쪽 클라이언트로 실제 HTTP 트래픽, 비동기 리스너로 실제 DB INSERT, 그 다음 `api_log` 행에 대한 단언.
- **`ReactiveApiClientUtilRoutingTest`** — 리액티브 verb 라우팅을 위한 빠른 mock 기반 단위 테스트.

### Fixed

- `ApiLogService.saveApiCallError`가 Spring WebFlux의 `WebClientResponseException` (HttpStatusCodeException과 별개 계층)에서 `status_code`와 `responseBody`를 추출하도록 수정. 이전에는 리액티브 4xx/5xx ERROR 행의 `status_code = NULL`이었음. `spring-webflux`를 안 가져오는 사용자에게 영향 없도록 리플렉션 duck-typing 사용.

### 의존성 노트

- `spring-webflux`와 `reactor-netty-http`는 `<optional>true</optional>` 선언 — 리액티브 클라이언트 안 쓰는 사용자는 무비용.
- test scope: `com.squareup.okhttp3:mockwebserver` 4.12.0, `io.projectreactor:reactor-test`.

### v0.5.0에서 마이그레이션

하위 호환 — v0.5.0의 모든 API 보존. 리액티브 클라이언트를 쓰려면 `spring-webflux` + `reactor-netty-http`를 의존성에 추가.

## [0.5.0] — PUT / DELETE / PATCH + 코어 API로 재시도 correlation

### Added

- **`RestApiClientUtil`에 PUT / DELETE / PATCH 편의 메서드** — 기존 GET/POST 패턴을 따르는 12개 신규 메서드: `putSync`, `putSyncTyped`, `putAsync`, `putAsyncTyped`, `deleteSync`, `deleteSyncTyped`, `deleteAsync`, `deleteAsyncTyped`, `patchSync`, `patchSyncTyped`, `patchAsync`, `patchAsyncTyped` (각각 `String` / 타입 바디 / 타입 응답 오버로드 적절히).
- **코어 `send` / `sendAsync` / `sendTyped` / `sendAsyncTyped` API** — `(HttpMethod, ApiRequest)`를 직접 받음. 호출자가 명시적 `requestId`를 전달해 재시도 시도들이 correlation 키를 공유 — v0.4.0 재시도 가이드에서 지적한 갭을 채움.

### Changed (내부 — 공개 API 변경 없음)

- `RestApiClientUtil` 리팩토링: 22개 공개 메서드가 모두 코어 4개 `send*` 메서드로 흐름. try/catch/이벤트 발행 중복 코드 약 270줄이 한 곳으로 정리. 동작은 v0.4.0과 동일.

### Tests

- 새 `RestApiClientUtilRoutingTest` — 각 동사 메서드가 올바른 `HttpMethod`로 라우팅되고 `send(HttpMethod, ApiRequest)`가 호출자 제공 `requestId`를 존중하는지 검증.

### v0.4.0에서 마이그레이션

완전히 하위 호환 — v0.4.0의 모든 메서드 시그니처와 동작 보존. 새 메서드는 추가만.

## [0.4.0] — 버그 픽스: 실제 상태 코드, 구조화된 에러, 정직해진 재시도 문서

### Fixed

- **`RestApiClientUtil` raw 메서드가 실제 HTTP 상태 코드를 보고.** `getSync`, `postSync(String, String)`, `getAsync`, `postAsync(String, String)`이 실제 응답과 무관하게 `statusCode = 200` 하드코딩 (201, 204 다 200으로 저장). 타입 메서드(`*Typed`)는 영향 없었음. 내부적으로 `.body(String.class)` → `.toEntity(String.class)`로 전환.
- **`ApiLogService.saveApiCallError`가 Spring 예외에서 HTTP 상태 추출.** ERROR / RETRY_ERROR 행의 `status_code`는 항상 NULL이었음. 이제 `HttpStatusCodeException` / `RestClientResponseException`에서 추출.
- **`error_message` JSONB 컬럼이 문서 형식과 일치.** 이전엔 `toJsonNode(message)` — 예외 메시지 문자열만 — 으로 저장되어 문서의 `{type, message}` 약속과 모순. 이제 구조화: `{"type": "<FQCN>", "message": "<메시지>" [, "responseBody": "<업스트림 본문>"]}`.

### Docs

- **`retry-handling` 가이드 재작성**: `RestApiClientUtil`은 재시도 컨텍스트를 전파하지 않음(매 재시도마다 새 `request_id`). 재시도 타임라인 추적의 지원 경로는 이벤트 직접 발행. `ApiEventListener`의 `@Retryable` 로그 쓰기 재시도 섹션 추가.
- **`error_message` 레퍼런스 업데이트**: 새 `responseBody` 필드 문서화 (본문을 가진 HTTP 예외에만 존재).
- README 스키마 컬럼 정정: `event_type VARCHAR(50)` (이전 20), `request_id VARCHAR(36)` (이전 255).
- "Production-tested" / "Spring Retry integration with RETRY_ERROR events" 잘못된 주장 제거.
- Maven Central + CI 배지 READMEs에 추가.

### v0.3.0에서 마이그레이션

대부분 호환 — 같은 API. 주의:

- **raw 메서드 SUCCESS 행의 `status_code`**: 항상 200이었음, 이제 실제 반영 (201, 204 등). `status_code = 200`을 하드코딩한 쿼리는 조정 필요.
- **`error_message` JSONB 형식**: 원시 메시지 문자열 또는 `{"raw": "..."}` fallback이었음. 이제 구조화 `{type, message, responseBody?}`. `error_message ->> 'raw'` 쿼리는 더 이상 매치 안 됨 — `error_message ->> 'message'` 사용.
- **ERROR 행의 `status_code`**: 항상 NULL이었음. 이제 Spring 예외에서 추출 (타임아웃 같은 비-HTTP 예외는 NULL).

## [0.3.0] — BUILTIN 스키마 관리가 새 기본값

### Changed

- **BUILTIN이 새 기본 스키마 관리 전략.** v0.2.0이 스키마 관리를 옵트인(`NONE` 기본)으로 만들었지만, Flyway/Liquibase 안 쓰는 사용자는 첫 부팅에서 "테이블 없음" 에러를 봐야 했습니다. v0.3.0은 기본값을 `BUILTIN`으로 뒤집어 — 스타터가 Spring Boot의 `DataSourceScriptDatabaseInitializer`로 `CREATE TABLE IF NOT EXISTS`를 자동 실행. 테이블이 그냥 존재합니다.
- `V1.0__create_api_log.sql`이 `IF NOT EXISTS` 절을 사용해 멱등적 — 매 부팅마다 안전하게 재실행 가능. Flyway도 문제 없음 (`flyway_schema_history` 행이 핵심, SQL 결과가 아님).

### 전략 정리 (v0.3.0 이후)

- `api.log.schema.management=builtin` (기본) — 스타터가 부팅 시 테이블 생성
- `api.log.schema.management=flyway` — 스타터가 `FlywayConfigurationCustomizer` 등록 (Flyway 의존성 필요)
- `api.log.schema.management=none` — 스타터가 스키마에 손 안 댐; 사용자가 직접 DDL 적용

### v0.2.0에서 마이그레이션

- **`management=flyway` 명시한 경우:** 변경 불필요.
- **`management=none` 명시한 경우:** 변경 불필요.
- **`management`를 설정 안 한 경우 (v0.2.0 기본 NONE에 의존, 다른 곳에서 DDL 적용 중):** `management=none`을 명시해서 기존 동작 유지하거나, BUILTIN이 동작하도록 그대로 두기 (멱등적이라 기존 테이블과 충돌 없음).

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
- 재시도 시도를 위한 `RETRY_ERROR` 이벤트 타입 (사용자가 직접 발행하거나 본인의 `@Retryable` 래퍼를 통해).
- `ApiLogAutoConfiguration`을 통한 자동 구성, `@ConditionalOnMissingBean` 오버라이드.
- 서비스·리포지토리·리스너·Testcontainers 기반 PostgreSQL 통합까지 포괄적 테스트.

[Unreleased]: https://github.com/devslab-kr/api-log/compare/v0.5.2...HEAD
[0.5.2]: https://github.com/devslab-kr/api-log/releases/tag/v0.5.2
[0.5.1]: https://github.com/devslab-kr/api-log/releases/tag/v0.5.1
[0.5.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.5.0
[0.4.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.4.0
[0.3.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.3.0
[0.2.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.2.0
[0.1.0]: https://github.com/devslab-kr/api-log/releases/tag/v0.1.0
