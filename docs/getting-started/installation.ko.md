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
        <version>0.1.0</version>
    </dependency>
    ```

=== "Gradle (Kotlin DSL)"

    ```kotlin
    dependencies {
        implementation("kr.devslab:api-log-spring-boot-starter:0.1.0")
    }
    ```

=== "Gradle (Groovy)"

    ```groovy
    dependencies {
        implementation 'kr.devslab:api-log-spring-boot-starter:0.1.0'
    }
    ```

!!! note "프리릴리스"
    0.1.0이 Maven Central에 올라가기 전에는 Sonatype OSSRH의 `0.1.0-SNAPSHOT`을 사용하거나 소스에서 빌드: `./mvnw install`.

## 스타터가 자동으로 가져오는 의존성

- `spring-boot-starter-data-jpa` (`ApiLogRepository`)
- `spring-boot-starter-web` (내장 `RestApiClientUtil`)
- `spring-retry` (재시도 인식 로깅)
- `jackson-module-blackbird` (고성능 JSON 직렬화)
- `flyway-core` + `flyway-database-postgresql` (`api_log` 스키마 자동 적용)
- `postgresql` JDBC 드라이버 (runtime)

## 직접 제공해야 하는 것

- **PostgreSQL `DataSource`** — 스타터가 DB 접속 정보를 만들어주지는 않습니다
- **`ObjectMapper` 빈** — Spring Boot 자동 구성으로 충분

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
    enabled: true   # 기본값 — false로 두면 리스너가 등록되지 않음
```

## 자동 구성이 하는 일

스타터가 클래스패스에 있고 `api.log.enabled`가 `true`(기본값)이면 `ApiLogAutoConfiguration`이 활성화되어 다음을 등록합니다:

- `ApiLogService` — 영속화 오케스트레이터 (`ObjectMapper` 빈이 있어야 활성화)
- `ApiEventListener` — 이벤트를 서비스로 연결하는 `@EventListener` (async)
- `RetryConfig` — Spring Retry 통합을 위한 `@EnableRetry` 활성화
- JPA `@EntityScan` 및 `@EnableJpaRepositories` (`kr.devslab.apilog.model`, `kr.devslab.apilog.repository`)

모든 빈은 `@ConditionalOnMissingBean`. 직접 빈을 정의하면 오버라이드됩니다.

## 스키마

번들된 Flyway 마이그레이션 `V1.0__create_api_log.sql`이 앱 첫 실행 시 `api_log` 테이블과 인덱스를 생성합니다. 프로젝트에서 Flyway를 사용 중이라면 자동으로 적용됩니다 — 컬럼 정보는 [레퍼런스 / 스키마](../reference/schema.md) 참고.

## 설치 확인

의존성을 추가하고 `DataSource`를 설정한 뒤 앱을 실행하면 Flyway가 마이그레이션을 적용하는 로그가 보입니다:

```text
o.f.c.i.command.DbMigrate : Migrating schema "public" to version "1.0 - create api log"
o.f.c.i.command.DbMigrate : Successfully applied 1 migration to schema "public"
```

이제 `api_log` 테이블이 존재합니다:

```sql
\d api_log
```

이어서 [빠른 시작](quickstart.md)으로 첫 번째 로그를 기록해보세요.
