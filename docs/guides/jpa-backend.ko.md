# JPA 백엔드

JPA 백엔드 (`api-log-jpa`)가 기본 선택지입니다. v0.5.x에서 출시된 동작 그대로,
이제 3개 백엔드 중 하나로 패키징됐을 뿐입니다. 이미 Spring Data JPA를 쓰고
있다면, 또는 굳이 리액티브로 갈 이유가 없다면 이 백엔드를 고르세요.

## 언제 선택하나

- Spring MVC + JPA 스택을 쓰고 있을 때.
- v0.5.x 동작을 그대로 원할 때 — `ApiLogEntity`, `ApiLogRepository`, JSONB
  컬럼용 Hibernate `@JdbcTypeCode(SqlTypes.JSON)` 매핑.
- 다른 앱 코드처럼 `JpaRepository`로 감사 로그를 조회하고 싶을 때.

WebFlux + R2DBC 환경이면 [`api-log-r2dbc`](r2dbc-backend.md), MyBatis 환경이면
[`api-log-mybatis`](mybatis-backend.md)가 더 적합합니다. `api_log` 스키마 자체는
세 백엔드 모두 동일합니다.

## 설치

=== "Maven"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-jpa</artifactId>
        <version>0.6.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    implementation("kr.devslab:api-log-jpa:3.0.0")
    ```

`api-log-jpa`는 `api-log-core` (이벤트, 리스너, HTTP 유틸)와
`spring-boot-starter-data-jpa`, PostgreSQL JDBC 드라이버를 transitive하게
가져옵니다. 추가로 넣을 거 없음 — Flyway는 옵셔널이고
`api.log.schema.management=flyway`일 때만 필요합니다.

## 자동으로 등록되는 빈

JPA 백엔드가 classpath에 있고 `api.log.enabled=true`(기본값)이면
`ApiLogJpaAutoConfiguration`이 활성화되어 다음을 등록합니다:

| 빈 | 역할 |
| --- | --- |
| `JpaApiLogWriter` | 코어 리스너가 이벤트를 라우팅하는 `ApiLogWriter` 구현체 |
| `ApiLogJpaSchemaInitializer` | `DataSource`에 `V1.0__create_api_log.sql` 실행 (BUILTIN 모드만) |
| `ApiLogRepository` (`@EnableJpaRepositories` 경유) | `ApiLogEntity` 용 Spring Data 리포지토리 |
| `ApiLogFlywayConfigurationCustomizer` | Flyway locations에 `classpath:db/api-log` 추가 (FLYWAY 모드만) |

`@EntityScan(basePackageClasses = ApiLogEntity.class)`이 자동으로 적용되므로
사용자 `@SpringBootApplication` 패키지 스캔에 `ApiLogEntity`를 별도로 추가할
필요 없습니다.

## 엔티티

```java
package kr.devslab.apilog.jpa.model;

@Entity
@Table(name = "api_log")
public class ApiLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventType;
    private String requestId;
    private String endpoint;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode response;

    private Integer statusCode;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode errorMessage;

    private LocalDateTime timestamp;
    private Integer retryCount;
    private Boolean isRetry;
}
```

JSONB 3개 컬럼 (`payload`, `response`, `error_message`)은
`@JdbcTypeCode(SqlTypes.JSON)`으로 Jackson `JsonNode`에 매핑됩니다.
Hibernate가 PostgreSQL 다이얼렉트의 JSONB 바인더로 위임해주기 때문에
JSON 구조가 그대로 보존됩니다 (단순 텍스트가 아닌).

## 코드에서 로그 조회

번들된 `ApiLogRepository`가 기본 조회를 제공:

```java
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final ApiLogRepository repo;

    public List<ApiLogEntity> timelineFor(String requestId) {
        return repo.findByRequestId(requestId);
    }

    public List<ApiLogEntity> errorsAt(String endpoint) {
        return repo.findByEndpoint(endpoint).stream()
                .filter(e -> "ERROR".equals(e.getEventType()))
                .toList();
    }
}
```

JSONB 연산자, GIN 인덱스 활용, 에러율 집계 같은 풍부한 질의는
[로그 조회 가이드](querying-logs.md) 참고 — 테이블 스키마가 같아서 세 백엔드
모두 동일하게 적용됩니다.

## 트랜잭션 시맨틱

`JpaApiLogWriter`의 모든 메서드는 `@Transactional(propagation = REQUIRES_NEW)`로
실행됩니다:

```java
@Override
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void writeInitiated(ApiCallInitiatedEvent event) { ... }
```

의도된 설계입니다. 감사 로그 쓰기는 호출자의 비즈니스 트랜잭션과 함께 롤백되면
안 됩니다 — 호출자의 `@Transactional`이 나중에 실패해서 롤백되더라도
`INITIATED` 행은 남아 있어야 합니다. 작업 단위의 운명과 무관하게 `api_log`를
보면 그 호출이 실제로 나갔는지 확인할 수 있어야 합니다.

## 스키마 관리

기본값은 `api.log.schema.management=builtin` — 부팅 시 번들된
`V1.0__create_api_log.sql`이 Spring Boot의 `DataSourceScriptDatabaseInitializer`를
통해 `DataSource`에 실행됩니다. DDL이 `IF NOT EXISTS`를 사용해서 멱등합니다.

```yaml title="application.yml"
api:
  log:
    schema:
      management: builtin   # 기본값
```

Flyway 모드로 전환 (마이그레이션이 `flyway_schema_history`에 기록됨):

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

```yaml title="application.yml"
api:
  log:
    schema:
      management: flyway
```

`ApiLogFlywayConfigurationCustomizer`가 기존 `spring.flyway.locations`에
`classpath:db/api-log`를 추가합니다 — 사용자 마이그레이션과 우리 마이그레이션이
하나의 sweep에서 돌고, 하나의 history 테이블을 공유합니다.

완전히 opt-out (DDL을 직접 적용):

```yaml
api:
  log:
    schema:
      management: none
```

각 전략의 자세한 동작과 원본 DDL은 [스키마 레퍼런스](../reference/schema.md)에
있습니다.

## Writer 오버라이드

로그 쓰기 방식을 커스터마이즈해야 할 때 (추가 컬럼, 페이로드 마스킹, 다른
테이블 등)는 직접 `ApiLogWriter` 빈을 정의하면 됩니다 — 백엔드의
`@ConditionalOnMissingBean(ApiLogWriter.class)`이 뒤로 빠집니다:

```java
@Bean
public ApiLogWriter customWriter(ApiLogRepository repo, PayloadJsonMapper json,
                                  PayloadMasker masker) {
    return new MaskingJpaApiLogWriter(repo, json, masker);
}
```

번들된 writer를 감싸는 패턴이 일반적입니다:

```java
public class MaskingJpaApiLogWriter implements ApiLogWriter {

    private final ApiLogWriter delegate;
    private final PayloadMasker masker;

    public MaskingJpaApiLogWriter(ApiLogRepository repo, PayloadJsonMapper json,
                                   PayloadMasker masker) {
        this.delegate = new JpaApiLogWriter(repo, json);
        this.masker = masker;
    }

    @Override
    public void writeInitiated(ApiCallInitiatedEvent event) {
        delegate.writeInitiated(masker.mask(event));
    }
    // ... writeSuccess / writeError도 동일
}
```

## 더 읽어볼 거리

- [로그 조회](querying-logs.md) — JSONB 연산자 레시피, 인덱스, 에러율.
- [이벤트 직접 발행](publishing-events.md) — 자체 HTTP 클라이언트 사용 시
  이벤트만 사용.
- [재시도 처리](retry-handling.md) — `RETRY_ERROR` 시맨틱, 리스너의 로그 쓰기
  재시도.
- [스키마 레퍼런스](../reference/schema.md) — 컬럼 타입, 인덱스, 원본 DDL.
