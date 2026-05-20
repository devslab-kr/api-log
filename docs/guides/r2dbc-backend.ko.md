# R2DBC 백엔드

R2DBC 백엔드 (`api-log-r2dbc`)는 리액티브 `ConnectionFactory`로 감사 행을
씁니다 — JDBC 드라이버도 없고, 블로킹 I/O도 없습니다. Spring WebFlux + R2DBC
스택에서 감사 로그도 리액티브 파이프라인에 그대로 흐르게 하고 싶을 때 (JDBC
브리지로 빠지지 않게) 이걸 고르세요.

## 언제 선택하나

- 애플리케이션 스택이 WebFlux + R2DBC.
- 런타임 classpath에 JDBC 드라이버를 명시적으로 두기 싫을 때.
- Spring Data R2DBC 리포지토리 대신 `DatabaseClient`를 직접 쓰는 게 괜찮을
  때 — 의존성 footprint를 최소로 유지하기 위한 의도된 트레이드오프입니다.

Servlet 스택이라면 JPA가 더 자연스럽습니다 — [`api-log-jpa`](jpa-backend.md)
선택. 두 백엔드는 `api_log`에 동일한 행을 만듭니다.

## 설치

=== "Maven"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-r2dbc</artifactId>
        <version>0.6.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    implementation("kr.devslab:api-log-r2dbc:0.6.0")
    ```

`api-log-r2dbc`는 `api-log-core`와 `spring-r2dbc` (`DatabaseClient`),
`r2dbc-postgresql` (runtime), `reactor-core`를 transitive하게 가져옵니다.
**JDBC 의존성 없음** — Hibernate, HikariCP, `spring-jdbc`가 사용자 앱에서
다른 경로로 들어오지 않는 한 classpath에 안 올라옵니다.

PostgreSQL용 `ConnectionFactory` 빈은 별도로 필요합니다 — 가장 쉬운 방법은
Spring Boot 자동 구성:

```yaml title="application.yml"
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/your_db
    username: your_user
    password: your_password
```

## 자동으로 등록되는 빈

`ConnectionFactory`가 classpath에 있고 `api.log.enabled=true`이면
`ApiLogR2dbcAutoConfiguration`이 활성화되어 다음을 등록합니다:

| 빈 | 역할 |
| --- | --- |
| `DatabaseClient` (`@ConditionalOnMissingBean`) | 사용자의 `ConnectionFactory`에서 구성 — Spring Boot가 이미 제공했다면 스킵 |
| `R2dbcApiLogWriter` | 코어 리스너가 이벤트를 라우팅하는 `ApiLogWriter` 구현체 |
| `ApiLogR2dbcSchemaInitializer` | Spring Boot의 `R2dbcScriptDatabaseInitializer`로 `V1.0__create_api_log.sql`을 리액티브하게 실행 (BUILTIN 모드만) |

스키마 초기화는 `ConnectionFactory`와만 통신합니다 — **JDBC DataSource가
필요 없음**, 부팅 시에도. v0.6.0에서 이 백엔드가 약속하는 핵심:
완전 리액티브 `api_log` 설치.

## 행이 어떻게 써지는가

`R2dbcApiLogWriter`는 Spring Data 리포지토리를 거치지 않습니다.
`DatabaseClient.sql(...)`로 파라미터화된 INSERT를 호출하고, fire-and-forget
의미로 인라인 subscribe 합니다:

```java
spec.fetch()
        .rowsUpdated()
        .subscribe(
                rows -> { /* success — 리스너가 이미 DEBUG로 로그 */ },
                ex -> log.error("R2DBC api_log insert failed: requestId={}, eventType={}",
                        requestId, eventType, ex)
        );
```

리스너는 반환된 리액티브 타입을 소비하지 않습니다 — 이벤트는 설계상
fire-and-forget이고, @Async 래핑이 `Mono`를 의미 있게 전달해주지도 않습니다.
구독 에러는 로깅되지만 절대 재throw 되지 않습니다 — 감사 행 하나를 잃는다고
사용자의 outbound HTTP 호출이 망가지면 안 되니까요.

### `::jsonb` 캐스트 없이 JSONB 바인딩

JSONB 3개 컬럼 (`payload`, `response`, `error_message`)은 `R2dbcType.CLOB`
(text)으로 바인딩됩니다:

```java
private static Object asJsonbParam(String value) {
    return value == null
            ? Parameters.in(R2dbcType.CLOB)
            : Parameters.in(R2dbcType.CLOB, value);
}
```

PostgreSQL R2DBC 드라이버가 컬럼 레벨에서 `TEXT → JSONB` 묵시적 캐스트를
처리해주므로 SQL에서 `::jsonb` 캐스트가 필요 없습니다 — 향후 다른 리액티브
다이얼렉트가 등장해도 INSERT는 그대로 휴대 가능합니다.

## 스키마 관리

기본값은 `api.log.schema.management=builtin`. 리액티브 초기화는 Spring Boot의
`R2dbcScriptDatabaseInitializer`를 사용해서 첫 연결 시 번들된 DDL을 실행합니다.
`IF NOT EXISTS` 덕분에 부팅 간 멱등합니다.

```yaml title="application.yml"
api:
  log:
    schema:
      management: builtin   # 기본값
```

**R2DBC에서는 Flyway 모드가 지원되지 않습니다.** Flyway는 JDBC `DataSource`를
요구합니다; Flyway 관리 스키마가 필요한 리액티브 앱은:

- Spring Boot 기본 Flyway 자동 구성을 R2DBC와 함께 설치 (Flyway는 마이그레이션
  전용으로 자체 JDBC 연결을 열고 — R2DBC 풀과 별개로 — 부팅 후 앱은 순수
  리액티브로 유지됨), 그리고 `spring.flyway.locations`에 `classpath:db/api-log`를
  직접 추가; 또는
- 순수 리액티브가 hard requirement가 아니면 JPA 백엔드로 전환.

`api.log.schema.management=none` (DDL 직접 적용)도 유효합니다:

```yaml
api:
  log:
    schema:
      management: none
```

## 리액티브 HTTP 클라이언트와의 조합

리액티브 백엔드는 [`ReactiveApiClientUtil`](reactive.md)과 자연스럽게
짝지어집니다 — `Mono<ApiResponse>`를 반환하면서 `R2dbcApiLogWriter`가 소비하는
이벤트를 발행합니다:

```java
@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ReactiveApiClientUtil api;

    public Mono<ChargeResult> charge(ChargeRequest input) {
        return api.postTyped("/charges", input, ChargeResult.class);
    }
}
```

End-to-end 리액티브: WebClient 호출 → 발행된 이벤트 → R2DBC writer →
PostgreSQL. 요청 경로 어디에도 블로킹 hop이 없습니다.

## Writer 오버라이드

행 쓰기 방식을 커스터마이즈해야 할 때 (마스킹, 추가 컬럼, 다른 테이블 등)는
직접 `ApiLogWriter` 빈을 정의 — 백엔드의
`@ConditionalOnMissingBean(ApiLogWriter.class)`가 뒤로 빠집니다:

```java
@Bean
public ApiLogWriter customWriter(DatabaseClient client, PayloadJsonMapper json) {
    return new TenantAwareR2dbcApiLogWriter(client, json, tenantContext);
}
```

위임 패턴이 일반적:

```java
public class TenantAwareR2dbcApiLogWriter implements ApiLogWriter {

    private final ApiLogWriter delegate;
    private final TenantContext tenants;

    public TenantAwareR2dbcApiLogWriter(DatabaseClient client, PayloadJsonMapper json,
                                         TenantContext tenants) {
        this.delegate = new R2dbcApiLogWriter(client, json);
        this.tenants = tenants;
    }

    @Override
    public void writeInitiated(ApiCallInitiatedEvent event) {
        delegate.writeInitiated(annotateTenant(event));
    }
    // ... writeSuccess / writeError도 동일
}
```

## 로그 조회

이 백엔드에는 리포지토리 추상화가 없습니다 — 행을 다시 읽어야 할 때는
`DatabaseClient`를 직접 사용:

```java
public Flux<Map<String, Object>> timelineFor(String requestId) {
    return databaseClient.sql("""
                    SELECT event_type, endpoint, status_code, timestamp
                    FROM api_log WHERE request_id = :rid ORDER BY id ASC
                    """)
            .bind("rid", requestId)
            .fetch()
            .all();
}
```

더 깊은 질의 (JSONB 연산자, GIN 인덱스, 에러율 등)는 [로그 조회
가이드](querying-logs.md) 참고 — SQL은 백엔드와 무관하게 동일합니다.

## 더 읽어볼 거리

- [리액티브 HTTP 클라이언트](reactive.md) — `ReactiveApiClientUtil`,
  이 writer가 소비하는 이벤트를 발행하는 WebClient 기반 짝꿍.
- [로그 조회](querying-logs.md) — JSONB 연산자 레시피, 인덱스, 에러율.
- [스키마 레퍼런스](../reference/schema.md) — 컬럼 타입, 인덱스, 원본 DDL.
