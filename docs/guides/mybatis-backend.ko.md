# MyBatis 백엔드

MyBatis 백엔드 (`api-log-mybatis`)는 `@Mapper`로 어노테이션된 인터페이스를
통해 감사 행을 씁니다. 이미 MyBatis를 쓰고 있는데 감사 로그 때문에 JPA /
Hibernate를 끌고 들어오기 싫을 때 이걸 고르세요.

## 언제 선택하나

- 이미 MyBatis 사용 중 (웹 스택은 무관 — Servlet 또는 WebFlux+JDBC).
- 프로젝트에 ORM을 하나만 두고 싶을 때. 감사 로깅 *만을 위해* JPA를 추가하면
  영속화 프레임워크 2개, 트랜잭션 매니저 2개, 컨벤션 2세트 — 보통은 그만한
  가치가 없습니다.

JPA를 쓰고 있으면 [`api-log-jpa`](jpa-backend.md) 선택. WebFlux + R2DBC에서
순수 리액티브 영속화를 원하면 [`api-log-r2dbc`](r2dbc-backend.md)가 맞습니다.
`api_log` 스키마는 세 백엔드 모두 동일합니다.

## 설치

=== "Maven"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-mybatis</artifactId>
        <version>3.0.1</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    implementation("kr.devslab:api-log-mybatis:3.0.1")
    ```

`api-log-mybatis`는 `api-log-core`, `mybatis-spring-boot-starter:3.0.4`
(Spring Boot 3.x 호환 라인), `spring-jdbc`, PostgreSQL JDBC 드라이버를
transitive하게 가져옵니다. `DataSource`는 별도로 구성해야 합니다 — Spring
Boot 기본 `spring.datasource.*`로 충분.

## 자동으로 등록되는 빈

MyBatis (`org.apache.ibatis.session.SqlSessionFactory`)가 classpath에 있고
`api.log.enabled=true`이면 `ApiLogMybatisAutoConfiguration`이 활성화되어
다음을 등록합니다:

| 빈 | 역할 |
| --- | --- |
| `MybatisApiLogWriter` | 코어 리스너가 이벤트를 라우팅하는 `ApiLogWriter` 구현체 |
| `ApiLogMapper` (`@MapperScan` 경유) | INSERT SQL을 담은 MyBatis `@Mapper` |
| `ApiLogMybatisSchemaInitializer` | `DataSource`에 `V1.0__create_api_log.sql` 실행 (BUILTIN 모드만) |

`@MapperScan(basePackageClasses = ApiLogMapper.class)`가 자동으로 적용됩니다.
사용자 애플리케이션에 이미 다른 패키지를 향한 `@MapperScan`이 있으면 우리 것이
함께 합쳐 동작합니다 — 두 스캔 모두 실행.

## 매퍼

```java
package kr.devslab.apilog.mybatis.mapper;

@Mapper
public interface ApiLogMapper {

    @Insert("""
            INSERT INTO api_log
                (event_type, request_id, endpoint, payload, response,
                 status_code, error_message, timestamp, retry_count, is_retry)
            VALUES
                (#{eventType},
                 #{requestId},
                 #{endpoint},
                 CAST(#{payload,jdbcType=VARCHAR} AS jsonb),
                 CAST(#{response,jdbcType=VARCHAR} AS jsonb),
                 #{statusCode,jdbcType=INTEGER},
                 CAST(#{errorMessage,jdbcType=VARCHAR} AS jsonb),
                 #{timestamp},
                 #{retryCount},
                 #{isRetry})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(ApiLogRow row);

    @Select("""
            SELECT id, event_type AS eventType, request_id AS requestId, endpoint,
                   payload::text AS payload, response::text AS response,
                   status_code AS statusCode, error_message::text AS errorMessage,
                   timestamp, retry_count AS retryCount, is_retry AS isRetry
            FROM api_log WHERE request_id = #{requestId} ORDER BY id ASC
            """)
    List<ApiLogRow> findByRequestId(String requestId);
}
```

### `CAST(...,jdbcType=VARCHAR) AS jsonb`를 쓰는 이유

PostgreSQL은 Java `String` 파라미터를 `JSONB` 컬럼으로 묵시적 캐스트해주지
않습니다. 두 가지 해결법:

1. **커스텀 `TypeHandler`** — 보일러플레이트, JSONB 컬럼마다 핸들러 등록 필요.
2. **SQL에서 명시적 캐스트** — 이 매퍼의 방식. 컬럼당 한 줄, 별도 와이어업
   없음.

`jdbcType=VARCHAR` 어노테이션은 값이 `null`일 때도 VARCHAR 바인딩을 강제해서
PostgreSQL의 "could not determine data type of parameter" 에러를 회피합니다.

### 행 타입

```java
public class ApiLogRow {
    private Long id;
    private String eventType;
    private String requestId;
    private String endpoint;
    private String payload;        // JSON 문자열 — PayloadJsonMapper의 canonical form
    private String response;       // JSON 문자열
    private Integer statusCode;
    private String errorMessage;   // JSON 문자열
    private LocalDateTime timestamp;
    private Integer retryCount;
    private Boolean isRetry;
}
```

"JSON" 필드 3개는 일반 `String`. `api-log-core`의
`PayloadJsonMapper.toJsonString()`이 canonical JSON 형태로 만들어주고, 매퍼의
캐스트가 insert 시점에 JSONB로 변환합니다.

## 트랜잭션 시맨틱

`MybatisApiLogWriter`의 메서드는 `@Transactional(propagation = REQUIRES_NEW)`로
실행됩니다:

```java
@Override
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void writeInitiated(ApiCallInitiatedEvent event) { ... }
```

JPA 백엔드와 동일한 계약 — 감사 쓰기는 호출자의 outer 트랜잭션과 함께 롤백되면
안 됩니다. 감사 행은 작업 단위의 나머지 운명과 무관하게 자체적으로
커밋됩니다.

## 스키마 관리

기본값은 `api.log.schema.management=builtin`. MyBatis 백엔드는 JDBC 기반
`DataSourceScriptDatabaseInitializer`를 사용 (JPA 백엔드와 같은 방식),
MyBatis 자체가 JDBC 위에서 동작하므로. DDL은 `IF NOT EXISTS`를 사용해서 매
부팅마다 다시 돌려도 no-op.

```yaml title="application.yml"
api:
  log:
    schema:
      management: builtin   # 기본값
```

`api-log-mybatis`에는 **Flyway 통합이 번들되지 않습니다** — `FlywayConfigurationCustomizer`는
`api-log-jpa`에만 들어 있습니다. MyBatis 앱이 Flyway로 테이블 관리를
원한다면:

- Flyway를 의존성에 추가 (`flyway-core` + `flyway-database-postgresql`).
- `spring.flyway.locations`에 `classpath:db/api-log`를 사용자 위치와 함께
  추가:

  ```yaml
  spring:
    flyway:
      locations:
        - classpath:db/migration   # 사용자 자신의 것
        - classpath:db/api-log     # 우리 것
  ```

- `api.log.schema.management=none`으로 설정해서 BUILTIN 초기화가 Flyway 부트스트랩과
  충돌하지 않게 함.

`none` (DDL을 Liquibase / `psql` 등으로 직접 적용)도 유효:

```yaml
api:
  log:
    schema:
      management: none
```

## 행 다시 읽기

번들된 `findByRequestId`가 "한 호출의 타임라인" 쿼리를 커버합니다. 그 외에는
직접 `ApiLogMapper`에 질의를 추가하세요 (번들된 걸 확장하거나, 별도 매퍼에):

```java
@Mapper
public interface MyApiLogQueries {

    @Select("""
            SELECT COUNT(*) FILTER (WHERE event_type = 'ERROR') * 100.0 / COUNT(*)
            FROM api_log
            WHERE endpoint = #{endpoint}
              AND timestamp > NOW() - INTERVAL '1 hour'
            """)
    Double errorRateLastHour(String endpoint);
}
```

JSONB 쿼리 플레이북 (연산자, GIN 인덱스, 에러율) 전체는 [로그 조회
가이드](querying-logs.md)에 — 백엔드와 무관하게 동일합니다.

## Writer 오버라이드

행 쓰기 방식을 커스터마이즈할 때는 직접 `ApiLogWriter` 빈을 정의 — 백엔드의
`@ConditionalOnMissingBean(ApiLogWriter.class)`가 뒤로 빠집니다:

```java
@Bean
public ApiLogWriter customWriter(ApiLogMapper mapper, PayloadJsonMapper json,
                                  PayloadMasker masker) {
    return new MaskingMybatisApiLogWriter(mapper, json, masker);
}
```

번들된 writer를 감싸는 게 보통 충분합니다:

```java
public class MaskingMybatisApiLogWriter implements ApiLogWriter {

    private final ApiLogWriter delegate;
    private final PayloadMasker masker;

    public MaskingMybatisApiLogWriter(ApiLogMapper mapper, PayloadJsonMapper json,
                                       PayloadMasker masker) {
        this.delegate = new MybatisApiLogWriter(mapper, json);
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

- [로그 조회](querying-logs.md) — JSONB 연산자 레시피, GIN 인덱스, 에러율.
- [이벤트 직접 발행](publishing-events.md) — 자체 HTTP 클라이언트 사용 시
  이벤트만 사용.
- [재시도 처리](retry-handling.md) — `RETRY_ERROR` 시맨틱, 리스너의 로그 쓰기
  재시도.
- [스키마 레퍼런스](../reference/schema.md) — 컬럼 타입, 인덱스, 원본 DDL.
