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
        <version>0.2.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("kr.devslab:api-log-spring-boot-starter:0.2.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'kr.devslab:api-log-spring-boot-starter:0.2.0'
    }
    ```

!!! tip "최신 버전"
    `0.2.0`은 [Maven Central](https://central.sonatype.com/artifact/kr.devslab/api-log-spring-boot-starter)의 최신 버전으로 교체.

## 스타터가 자동으로 가져오는 의존성

- `spring-boot-starter-data-jpa` (`ApiLogRepository`)
- `spring-boot-starter-web` (내장 `RestApiClientUtil`)
- `spring-retry` (재시도 인식 로깅)
- `jackson-module-blackbird` (고성능 JSON 직렬화)
- `postgresql` JDBC 드라이버 (runtime)

!!! info "Flyway는 옵셔널 (v0.2.0부터)"
    Flyway는 더 이상 transitive 의존성이 아닙니다. 번들된 마이그레이션을 자동으로 적용하려면 ([스키마 관리](#schema-management) 참고) 직접 추가하세요:

    === "Maven"

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

    === "Gradle (Kotlin DSL)"

        ```kotlin
        implementation("org.flywaydb:flyway-core")
        runtimeOnly("org.flywaydb:flyway-database-postgresql")
        ```

## 직접 제공해야 하는 것

- **PostgreSQL `DataSource`** — 스타터가 DB 접속 정보를 만들어주지는 않습니다
- **`ObjectMapper` 빈** — Spring Boot 자동 구성으로 충분
- `api_log` 테이블 생성 방법 — DDL을 직접 적용하거나, 번들 Flyway 마이그레이션을 옵트인 (아래)

```yaml title="application.yml"
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_db
    username: your_user
    password: your_password
  threads:
    virtual:
      enabled: true   # Java 21+ 권장

api:
  log:
    enabled: true              # 기본값 — false면 전체 인프라 비활성화
    schema:
      management: none         # 기본값 — 아래 "스키마 관리" 참고
```

## 자동 구성이 하는 일

스타터가 클래스패스에 있고 `api.log.enabled`가 `true`(기본값)이면 `ApiLogAutoConfiguration`이 활성화되어 다음을 등록합니다:

- `ApiLogService` — 영속화 오케스트레이터 (`ObjectMapper` 빈이 있어야 활성화)
- `ApiEventListener` — 이벤트를 서비스로 연결하는 `@EventListener` (async)
- `RetryConfig` — Spring Retry 통합을 위한 `@EnableRetry` 활성화
- JPA `@EntityScan` 및 `@EnableJpaRepositories` (`kr.devslab.apilog.model`, `kr.devslab.apilog.repository`)

모든 빈은 `@ConditionalOnMissingBean`. 직접 빈을 정의하면 오버라이드됩니다.

## 스키마 관리 { #schema-management }

`api_log` 테이블은 **자동으로 생성되지 않습니다.** `api.log.schema.management`로 어떻게 만들지 선택합니다:

=== "NONE (기본) — DDL 직접 적용"

    [스키마 레퍼런스](../reference/schema.md)의 SQL을 다음 중 하나에 넣으세요:

    - 본인 프로젝트의 Flyway 마이그레이션, 또는
    - 본인 프로젝트의 Liquibase changelog, 또는
    - 배포 시 수동 `psql` 실행, 또는
    - 본인이 이미 쓰고 있는 스키마 관리 흐름

    **기본값**입니다 — 운영 환경 대부분은 이미 마이그레이션을 관리하고 있고, 서드파티 라이브러리가 자기 마음대로 스키마에 손대는 걸 원하지 않기 때문입니다.

=== "FLYWAY — 스타터에 위임"

    위에서 Flyway 의존성 추가 후:

    ```yaml title="application.yml"
    api:
      log:
        schema:
          management: flyway
    ```

    스타터가 `FlywayConfigurationCustomizer`를 등록해 기존 `spring.flyway.locations`에 `classpath:db/api-log`를 추가합니다. 본인 마이그레이션과 우리 마이그레이션이 함께 실행됨 — 충돌·중복 없음.

    번들 마이그레이션 `V1.0__create_api_log.sql`이 `api_log` 테이블과 `request_id`, `timestamp` 인덱스를 생성합니다.

## 설치 확인

의존성 추가 후 앱 실행 시 두 가지로 확인:

1. **자동 구성 로드** — `--debug` 옵션으로 `ApiLogAutoConfiguration matched` 로그
2. **`api_log` 테이블 존재** — DDL을 수동으로 적용했거나, Flyway가 다음 로그를 남겼거나:
    ```text
    o.f.c.i.command.DbMigrate : Migrating schema "public" to version "1.0 - create api log"
    o.f.c.i.command.DbMigrate : Successfully applied 1 migration to schema "public"
    ```

이어서 [빠른 시작](quickstart.md)으로 첫 번째 로그를 기록해보세요.
