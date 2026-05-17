# 설치

## 요구사항

- **Java 21+** (Virtual Threads 권장이지만 필수는 아님)
- **Spring Boot 3.5+**
- **PostgreSQL 15+** (저장 레이어가 JSONB에 의존; 구버전도 작동하지만 일부 연산자 제약)

## 의존성 추가

=== "Maven"

    ```xml
    <dependency>
        <groupId>kr.devslab</groupId>
        <artifactId>api-log-spring-boot-starter</artifactId>
        <version>0.3.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("kr.devslab:api-log-spring-boot-starter:0.3.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'kr.devslab:api-log-spring-boot-starter:0.3.0'
    }
    ```

!!! tip "최신 버전"
    `0.3.0`은 [Maven Central](https://central.sonatype.com/artifact/kr.devslab/api-log-spring-boot-starter)의 최신 버전으로 교체.

## 스타터가 자동으로 가져오는 의존성

- `spring-boot-starter-data-jpa` (`ApiLogRepository`)
- `spring-boot-starter-web` (내장 `RestApiClientUtil`)
- `spring-retry` (재시도 인식 로깅)
- `jackson-module-blackbird` (고성능 JSON 직렬화)
- `postgresql` JDBC 드라이버 (runtime)

Flyway는 **옵셔널** — `api.log.schema.management=flyway`로 설정할 때만 필요합니다 (아래 [스키마 관리](#schema-management) 참고). 기본값(BUILTIN)은 Flyway 불필요.

## 직접 제공해야 하는 것

- **PostgreSQL `DataSource`** — 스타터가 DB 접속 정보를 만들어주지는 않습니다
- **`ObjectMapper` 빈** — Spring Boot 자동 구성으로 충분

여기까지. 기본 설정이면 `api_log` 테이블은 첫 부팅에 자동 생성됩니다.

```yaml title="application.yml"
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_db
    username: your_user
    password: your_password
  threads:
    virtual:
      enabled: true   # Java 21+ 권장

# 둘 다 합리적인 기본값 — 비기본 동작이 필요할 때만 명시.
api:
  log:
    enabled: true              # 기본값 — false면 전체 인프라 비활성화
    schema:
      management: builtin      # 기본값 — 아래 "스키마 관리" 참고
```

## 자동 구성이 하는 일

스타터가 클래스패스에 있고 `api.log.enabled`가 `true`(기본값)이면 `ApiLogAutoConfiguration`이 활성화되어 다음을 등록합니다:

- `ApiLogService` — 영속화 오케스트레이터 (`ObjectMapper` 빈이 있어야 활성화)
- `ApiEventListener` — 이벤트를 서비스로 연결하는 `@EventListener` (async)
- `RetryConfig` — Spring Retry 통합을 위한 `@EnableRetry` 활성화
- `ApiLogSchemaInitializer` — 부팅 시 `CREATE TABLE IF NOT EXISTS` 실행 (`schema.management=builtin` 활성화 시, 즉 기본값)
- JPA `@EntityScan` 및 `@EnableJpaRepositories` (`kr.devslab.apilog.model`, `kr.devslab.apilog.repository`)

모든 빈은 `@ConditionalOnMissingBean`. 직접 빈을 정의하면 오버라이드됩니다.

## 스키마 관리 { #schema-management }

`api.log.schema.management`로 `api_log` 테이블 생성 방식 선택:

=== "BUILTIN (기본) — 스타터가 처리"

    매 부팅마다 스타터가 번들된 `V1.0__create_api_log.sql`을 사용자의 `DataSource`에 실행합니다. DDL이 `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`이라 멱등적 — 테이블이 이미 있으면 매번 no-op.

    추가 의존성 없음. 외부 마이그레이션 도구 없음. 그냥 동작.

    Spring Boot의 [`DataSourceScriptDatabaseInitializer`](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/jdbc/init/DataSourceScriptDatabaseInitializer.html)를 통해 적용되므로 JPA 엔티티 검증보다 먼저 실행 — 빈 DB에서 Hibernate `ddl-auto=validate`가 실패하지 않음.

=== "FLYWAY — 사용자 Flyway에 등록"

    Flyway 의존성 추가:

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

    그리고 설정:

    ```yaml title="application.yml"
    api:
      log:
        schema:
          management: flyway
    ```

    스타터가 `FlywayConfigurationCustomizer`를 등록해 기존 `spring.flyway.locations`에 `classpath:db/api-log` 추가. 본인 마이그레이션과 함께 실행되며 `flyway_schema_history`에 `V1.0__create_api_log`가 추적됨.

    팀이 `flyway_schema_history` 행을 스키마 변경의 권위 있는 기록으로 다룬다면 이 옵션을 선택.

=== "NONE — DDL 직접 적용"

    스타터가 스키마에 손 안 댐. [스키마 레퍼런스](../reference/schema.md)의 SQL을 다음 중 하나에 넣으세요:

    - 본인 프로젝트의 Liquibase changelog, 또는
    - 배포 시 수동 `psql` 실행, 또는
    - 본인의 시작 스크립트

    ```yaml title="application.yml"
    api:
      log:
        schema:
          management: none
    ```

    팀 정책상 서드파티 라이브러리가 스키마에 손대지 못하게 하거나, 다른 인프라로 이미 테이블이 만들어져 있어 충돌을 피하고 싶을 때 선택.

## 설치 확인

의존성 추가 후 앱 실행 시:

1. **자동 구성 로드** — `--debug` 옵션으로 `ApiLogAutoConfiguration matched` 로그
2. **`api_log` 테이블 존재** — BUILTIN이면 자동 생성. FLYWAY면 Flyway 표준 "applied 1 migration" 로그. NONE이면 본인이 적용.

이어서 [빠른 시작](quickstart.md)으로 첫 번째 로그를 기록해보세요.
