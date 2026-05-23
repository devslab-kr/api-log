# api-log-spring-boot-starter

[English](README.md) · **한국어**

> Spring Boot용 이벤트 드리븐 API 호출 로깅. 비동기 이벤트 파이프라인 + PostgreSQL JSONB. 요청 경로를 막지 않고 외부 API 호출을 모두 기록합니다.

[![Maven Central](https://img.shields.io/maven-central/v/kr.devslab/api-log-core.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/kr.devslab/api-log-core)
[![CI](https://github.com/devslab-kr/api-log/actions/workflows/ci.yml/badge.svg)](https://github.com/devslab-kr/api-log/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/devslab-kr/api-log/branch/master/graph/badge.svg)](https://codecov.io/gh/devslab-kr/api-log)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5+-green.svg)](https://spring.io/projects/spring-boot)

📖 **[문서 → api-log.devslab.kr](https://api-log.devslab.kr/ko/)**

## 무엇을 하나

서비스의 모든 외부 HTTP 호출 — 요청, 응답, 에러, 재시도 — 을 PostgreSQL에 기록합니다. 논블로킹 이벤트 파이프라인으로 동작하므로 호출자는 로그 쓰기를 기다리지 않습니다. 번들된 `RestApiClientUtil`로 보낸 호출이든, 직접 발행하는 이벤트든, JSONB로 저장됩니다.

## 한눈에 보기

```java
@Service
public class UserService {

    private final RestApiClientUtil api;

    public UserService(RestApiClientUtil api) {
        this.api = api;
    }

    public User createUser(User newUser) {
        // HTTP 호출은 동기적이고; 로깅 이벤트는 백그라운드에서 발화.
        return api.postSyncTyped("/api/users", newUser, User.class);
    }
}
```

한 호출이 `api_log`에 한 행 이상 생성:

- **INITIATED** — 요청 발사
- **SUCCESS** / **ERROR** — 종료 결과 (상태 코드 + 페이로드)
- **RETRY_ERROR** — 재시도 실패 시 (사용자가 직접 발행)

본문은 JSONB로 저장되어 `->`, `->>`, GIN 인덱스로 자유롭게 조회 가능.

## 핵심 가치

- **논블로킹** — 로그 쓰기는 별도 스레드. HTTP 호출 경로는 절대 막히지 않음.
- **PostgreSQL JSONB** — 요청·응답·에러 본문이 조회 가능한 JSON
- **재시도 인식 스키마** — `RETRY_ERROR` 이벤트 + `retry_count` / `is_retry` 컬럼. 리스너도 일시적 DB 실패에 대해 로그 쓰기를 3회 재시도.
- **Virtual Threads 지원** — Java 21+ 비동기 설계
- **Drop-in 스타터** — 자동 구성으로 모든 빈 등록. 직접 빈 정의하면 오버라이드.

## 아키텍처

```
Caller code
   ↓
RestApiClientUtil / ReactiveApiClientUtil  (또는 자체 HTTP 클라이언트)
   ↓ publishEvent
ApplicationEventPublisher
   ↓ @EventListener (virtual threads)
ApiEventListener  (api-log-core)
   ↓ ApiLogWriter (SPI)
   ├─ JpaApiLogWriter      (api-log-jpa)
   ├─ R2dbcApiLogWriter    (api-log-r2dbc)
   └─ MybatisApiLogWriter  (api-log-mybatis)
        ↓
   PostgreSQL  (api_log · JSONB columns)
```

## 설치

v0.6.0부터 스타터가 4개 아티팩트로 분리됐습니다 — 백엔드 비종속 코어 1개 +
영속화 백엔드 1개. **`api-log-core` 1개 + 백엔드 1개**를 직접 골라 추가:

| 좌표 | 언제 쓰나 |
| --- | --- |
| `kr.devslab:api-log-jpa` | Servlet / JPA 앱 (v0.5.x 드롭인) |
| `kr.devslab:api-log-r2dbc` | WebFlux / R2DBC 앱 — JDBC 의존성 없음 |
| `kr.devslab:api-log-mybatis` | 이미 MyBatis를 쓰고, JPA를 원치 않을 때 |

백엔드 아티팩트 각각이 `api-log-core`를 transitive하게 가져오므로
좌표 하나만 추가하면 됩니다.

### Maven

```xml
<!-- JPA (가장 흔함 — v0.5.x 드롭인) -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>api-log-jpa</artifactId>
    <version>0.6.0</version>
</dependency>

<!-- 또는 리액티브 앱에서 R2DBC -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>api-log-r2dbc</artifactId>
    <version>0.6.0</version>
</dependency>

<!-- 또는 MyBatis -->
<dependency>
    <groupId>kr.devslab</groupId>
    <artifactId>api-log-mybatis</artifactId>
    <version>0.6.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("kr.devslab:api-log-jpa:3.0.0")
// 또는 "kr.devslab:api-log-r2dbc:3.0.0"
// 또는 "kr.devslab:api-log-mybatis:3.0.0"
```

## 설정

```yaml
api:
  log:
    enabled: true              # 기본값 — false면 전체 인프라 비활성화
    schema:
      management: builtin      # 기본값 — 아래 "스키마" 참고
```

직접 제공:

- PostgreSQL을 가리키는 `DataSource`
- `ObjectMapper` 빈 (Spring Boot 자동 구성으로 충분)

기본 설정이면 `api_log` 테이블은 첫 부팅 시 자동 생성 — 별도 설정 없이 동작합니다.

### 스키마 관리

`api.log.schema.management`로 `api_log` 테이블 생성 방식 선택:

- **`builtin`** (기본) — 스타터가 부팅 시 `CREATE TABLE IF NOT EXISTS` 실행. 멱등적, 마이그레이션 도구 불필요.
- **`flyway`** — 사용자 Flyway 흐름에 등록 (`flyway_schema_history`로 추적). `flyway-core` 클래스패스 필요.
- **`none`** — 스타터가 스키마에 손 안 댐. DDL 직접 적용.

전체 설치 가이드: [api-log.devslab.kr/ko/getting-started/installation](https://api-log.devslab.kr/ko/getting-started/installation/).

## `RestApiClientUtil` 사용

```java
// GET
ApiResponse r = api.getSync("/api/users/1");
User user    = api.getSyncTyped("/api/users/1", User.class);

// POST
ApiResponse r = api.postSync("/api/users", payload);
User created = api.postSyncTyped("/api/users", payload, User.class);

// 비동기
CompletableFuture<ApiResponse> f = api.postAsync("/api/users", payload);
```

자세한 API: [RestApiClientUtil 가이드](https://api-log.devslab.kr/ko/guides/using-restapiclient/).

## 이벤트 직접 발행

자체 HTTP 클라이언트를 쓰면서 로깅만 활용하고 싶을 때:

```java
@Service
@RequiredArgsConstructor
public class MyClient {

    private final ApplicationEventPublisher publisher;

    public void call() {
        ApiRequest req = ApiRequest.builder()
                .endpoint("/external/users")
                .payload("{\"name\":\"John\"}")
                .build();

        publisher.publishEvent(new ApiCallInitiatedEvent(this, req));
        try {
            ApiResponse res = doHttp(req);              // 자체 HTTP 호출
            publisher.publishEvent(new ApiCallSuccessEvent(this, req, res));
        } catch (Exception e) {
            publisher.publishEvent(new ApiCallErrorEvent(this, req, e, 0, false));
        }
    }
}
```

자세한 패턴 (재시도 타임라인 등): [이벤트 발행 가이드](https://api-log.devslab.kr/ko/guides/publishing-events/) · [재시도 처리 가이드](https://api-log.devslab.kr/ko/guides/retry-handling/).

## 스키마

| 컬럼 | 타입 | 비고 |
|---|---|---|
| `id` | BIGSERIAL | PK |
| `event_type` | VARCHAR(50) | `INITIATED`, `SUCCESS`, `ERROR`, `RETRY_ERROR` |
| `request_id` | VARCHAR(36) | UUID correlation id |
| `endpoint` | VARCHAR(255) | 대상 URL |
| `payload` | JSONB | 요청 본문 |
| `response` | JSONB | 응답 본문 |
| `error_message` | JSONB | `{type, message, responseBody?}` |
| `status_code` | INTEGER | HTTP 상태 (HTTP 예외에서 추출, 아니면 NULL) |
| `timestamp` | TIMESTAMP | 이벤트 발화 시각 |
| `retry_count` | INTEGER | 첫 시도는 `0` |
| `is_retry` | BOOLEAN | 재시도 시도면 `true` |

전체 컬럼 + 권장 인덱스 + JSONB 형식: [스키마 레퍼런스](https://api-log.devslab.kr/ko/reference/schema/).

## 예시 쿼리

```sql
-- 최근 1시간 엔드포인트별 에러율
SELECT endpoint,
       COUNT(*) FILTER (WHERE event_type = 'ERROR') * 100.0 / COUNT(*) AS error_rate
FROM api_log
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY endpoint
HAVING COUNT(*) > 10
ORDER BY error_rate DESC;
```

더 많은 패턴: [로그 조회 가이드](https://api-log.devslab.kr/ko/guides/querying-logs/).

## 요구사항

- Java 21+
- Spring Boot 3.5+
- PostgreSQL 15+ (JSONB)

## 라이선스

Apache License 2.0 — [LICENSE](LICENSE), [NOTICE](NOTICE) 참고.

---

[Devslab](https://devslab.kr) 제작 · [DevsLab 오픈소스 모음](https://github.com/devslab-kr)의 일부.
