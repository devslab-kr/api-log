# 설정

모든 설정은 `api.log` 접두사 아래. 스타터는 합리적인 기본값을 제공 — 대부분 프로젝트는 아무것도 설정 안 해도 됩니다.

## 속성

| 속성 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `api.log.enabled` | `boolean` | `true` | 마스터 스위치. `false`면 `ApiLogAutoConfiguration`이 비활성화되어 어떤 빈도 등록되지 않습니다 (리스너, 서비스, `RestApiClientUtil` 모두 없음). |
| `api.log.schema.management` | `NONE` \| `FLYWAY` | `NONE` | `api_log` 테이블을 어떻게 만들지. `NONE` — 사용자가 직접 DDL 적용 ([스키마](schema.md) 참고). `FLYWAY` — 스타터가 `classpath:db/api-log`를 `spring.flyway.locations`에 추가해 번들 마이그레이션이 사용자 마이그레이션과 함께 실행됨. `FLYWAY` 사용 시 `org.flywaydb:flyway-core` 직접 추가 필요 (스타터에서 optional로 선언). |

표면적이 이게 전부입니다 — 스타터는 의도적으로 최소화. 대부분의 동작(재시도 정책, 비동기 executor, Flyway 위치)은 표준 Spring Boot 설정에서 옵니다.

## 런타임에 비활성화

```yaml title="application.yml"
api:
  log:
    enabled: false
```

비활성화 시:

- `ApiEventListener` 등록 안 됨 → 누군가 이벤트 발행해도 무시됨
- `ApiLogService` 등록 안 됨 → `api_log`에 쓰기 없음
- `RestApiClientUtil` 등록 안 됨 (서비스에 의존)
- Flyway는 여전히 마이그레이션을 적용 (테이블은 어쨌든 생성) — 이것도 건너뛰려면 `db/migration/V1.0__create_api_log.sql`을 Flyway 위치에서 제외

## 운영에서 중요한 표준 Spring Boot 설정들

`api-log` 전용은 아니지만 프로덕션에서 튜닝할 가능성이 높은 것들:

### 비동기 executor

`@Async` 리스너는 Spring Boot 기본 `taskExecutor`를 사용. 커스터마이징 (큐 제한, 풀 크기 등):

```yaml title="application.yml"
spring:
  task:
    execution:
      pool:
        core-size: 8
        max-size: 32
        queue-capacity: 1000
        keep-alive: 60s
      thread-name-prefix: api-log-
```

Java 21 + Virtual Threads에서는 executor 자체가 대체됩니다:

```yaml title="application.yml"
spring:
  threads:
    virtual:
      enabled: true
```

`api-log`에 권장됩니다 — Virtual Threads는 "DB 쓰기 대기가 많은" 워크로드에 딱 맞습니다.

### Flyway 마이그레이션 위치

`api.log.schema.management=flyway`이면 스타터가 `spring.flyway.locations`에 `classpath:db/api-log`를 추가합니다. 기존 항목은 그대로 유지:

```yaml title="application.yml"
spring:
  flyway:
    locations:
      - classpath:db/migration       # 본인 마이그레이션 (Spring Boot 기본 경로)
      # api-log의 classpath:db/api-log는 스타터가 자동으로 추가
```

`classpath:db/api-log`를 직접 선언할 필요 없습니다. 만약 명시적으로 선언하면 locations 리스트에 두 번 나타남 — Flyway는 문제 없지만 시끄럽습니다. 빼고 customizer에 맡기세요.

### JPA 속성

스타터는 `spring-boot-starter-data-jpa`에 의존합니다. JPA 설정은 직접:

```yaml title="application.yml"
spring:
  jpa:
    hibernate:
      ddl-auto: validate     # 권장 — Flyway가 스키마 주체
    show-sql: false          # 디버깅 때만 true
    properties:
      hibernate:
        jdbc:
          batch_size: 50     # 리스너는 이벤트당 행 하나 쓰므로 배치가 큰 효과 없음
```

### DataSource 풀

기본 설정에서 리스너는 이벤트당 행 하나를 씁니다. HikariCP 풀 사이징:

```yaml title="application.yml"
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

정확한 숫자는 트래픽에 따라 — 기본 10으로 시작, `hikaricp_connections_active` 메트릭 보면서 조정.

## 자동 구성된 빈 오버라이드

같은 타입의 빈을 직접 정의하면 스타터의 `@ConditionalOnMissingBean`이 양보합니다:

```java
@Configuration
public class CustomApiLogConfig {

    @Bean
    public ApiLogService apiLogService(ApiLogRepository repo, ObjectMapper mapper) {
        return new MyCustomApiLogService(repo, mapper);   // 직접 만든 서브클래스
    }
}
```

`ApiEventListener`와 `RestApiClientUtil`도 같은 패턴.

## 같이 보기

- [스키마 레퍼런스](schema.md) — `api_log` 테이블 컬럼
- [이벤트 레퍼런스](events.md) — 이벤트 타입과 형식
