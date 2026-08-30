---
title: api-log — Spring Boot용 이벤트 드리븐 API 호출 로깅
---

# api-log

<a class="oss-project-intro" data-atmosphere="project" href="https://devslab.kr/brand/open-source/" aria-label="api-log, DevsLab 오픈소스">
  <img src="assets/logo.svg" alt="" aria-hidden="true" />
  <span><strong>api-log</strong><small>DevsLab 오픈소스</small></span>
</a>

> **Spring Boot용 이벤트 드리븐 API 호출 로깅.**
> 비동기 이벤트 파이프라인 + PostgreSQL JSONB. 요청 경로를 막지 않고 외부 API 호출을 모두 기록합니다.

[:fontawesome-solid-rocket: 시작하기](getting-started/installation.md){ .md-button .md-button--primary }
[:fontawesome-brands-github: GitHub](https://github.com/devslab-kr/api-log){ .md-button }

---

## 한눈에 보기

스타터를 클래스패스에 올리면 — 요청 본문, 응답 본문, 상태 코드, 에러, 재시도까지 — 모든 외부 HTTP 호출이 자동으로 PostgreSQL에 기록됩니다. HTTP 호출은 로그 쓰기를 기다리지 않습니다. 이벤트가 비동기로 처리됩니다.

```java
@Service
public class UserService {

    private final RestApiClientUtil api;

    public UserService(RestApiClientUtil api) {
        this.api = api;
    }

    public User createUser(User newUser) {
        // HTTP 호출은 동기적으로 즉시 반환됩니다.
        // 백그라운드에서 INITIATED → SUCCESS 또는 ERROR 두 줄이 api_log에 기록됩니다.
        return api.postSyncTyped("/api/users", newUser, User.class);
    }
}
```

한 번의 호출로 `api_log` 테이블에:

```sql
SELECT event_type, endpoint, status_code, timestamp FROM api_log ORDER BY id DESC LIMIT 3;

 event_type | endpoint     | status_code | timestamp
------------+--------------+-------------+----------------------
 SUCCESS    | /api/users   |         201 | 2026-05-18 10:02:14
 INITIATED  | /api/users   |             | 2026-05-18 10:02:14
```

본문(`payload`, `response`, `error_message`)은 JSONB로 저장되어 `->`, `->>`, GIN 인덱스로 자유롭게 조회 가능합니다. "그 벤더에 뭘 보냈는지 모르겠어"가 더 이상 없습니다.

## 핵심 가치

<div class="grid cards" markdown>

-   :material-flash: **논블로킹 설계**

    `ApplicationEventPublisher`로 별도 스레드에서 로그 기록. HTTP 호출은 응답이 오는 즉시 리턴되고, 로그 행은 그 뒤에 비동기로 영속화됩니다.

-   :material-database: **PostgreSQL JSONB 저장**

    요청·응답·에러 본문이 JSONB로 저장. GIN 인덱스, `->`, `->>`, `jsonb_path_query` 그대로 사용. 필요한 곳은 구조화, 유연해야 할 곳은 스키마리스.

-   :material-restart: **재시도 인식 스키마**

    `RETRY_ERROR` 이벤트 타입 + `retry_count` / `is_retry` 컬럼으로 불안정한 호출의 매 시도를 기록 가능. 리스너 자체도 일시적 DB 실패에 대해 로그 쓰기를 3번 재시도하므로 흔들리는 연결로 로그 행이 손실되지 않음.

-   :material-rocket-launch: **Virtual Threads 지원**

    Java 21+ 비동기에 맞춰 설계. `spring.threads.virtual.enabled=true`와 호환 — 적은 메모리로 높은 동시성.

-   :material-puzzle: **Drop-in 스타터**

    자동 구성으로 `ApiEventListener`, `ApiLogService`, `RestApiClientUtil`을 `@ConditionalOnMissingBean`으로 등록. 직접 빈을 정의하면 모두 오버라이드 가능.

-   :material-shield-check: **CI 검증**

    서비스·리포지토리·리스너·Testcontainers 기반 PostgreSQL 통합까지 커버하는 포괄적 테스트 스위트 — CI에서 매 PR/푸시마다 실행.

</div>

## 언제 쓰면 좋은가

**`api-log`가 적합한 경우** — 외부 API 호출 이력이 영구적이고 조회 가능해야 할 때. 벤더 통합 디버깅, 컴플라이언스 감사, 결제 정산, 고객용 사용량 리포트 작성 등.

**다른 도구가 더 적합한 경우** — 일시적 관측만 필요하다면 OpenTelemetry + 트레이스 백엔드, 단순 fire-and-forget 전송 로그라면 stdout + 로그 수집기.

## 아키텍처

```
Caller code
   ↓
RestApiClientUtil  (또는 자체 HTTP 클라이언트)
   ↓ publishEvent
ApplicationEventPublisher
   ↓ @EventListener (async)
ApiEventListener
   ↓
ApiLogService
   ↓
ApiLogRepository  (JPA)
   ↓
PostgreSQL  (api_log · JSONB columns)
```

호출마다 최소 두 행이 생성됩니다: `INITIATED` (즉시) 그리고 종료 행 (`SUCCESS`, `ERROR`, 또는 `RETRY_ERROR`). 모든 이벤트가 `request_id`로 연결되어 한 호출의 전체 흐름을 단일 쿼리로 복원할 수 있습니다.

## 다음 단계

- [스타터 설치](getting-started/installation.md) — Maven, Gradle 의존성
- [빠른 시작](getting-started/quickstart.md) — 5분 안에 첫 로그 행 만들기
- [`RestApiClientUtil` 사용하기](guides/using-restapiclient.md) — 내장 HTTP 클라이언트
- [이벤트 직접 발행](guides/publishing-events.md) — 자체 HTTP 클라이언트와 조합
- [레퍼런스 / 스키마](reference/schema.md) — `api_log` 테이블 구조
