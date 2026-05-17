# 빠른 시작

5분 만에 외부 HTTP 호출을 보내고, `api_log`에 행이 기록되는 것을 확인하고, 다시 조회해보겠습니다.

## 1. Base URL 설정

`RestApiClientUtil`은 base URL이 설정된 `RestClient` 인스턴스 하나를 사용합니다:

```yaml title="application.yml"
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_db
    username: your_user
    password: your_password
```

이게 전부 — happy path에서 `api.log.*` 설정 필요 없음. 기본값 `schema.management=builtin`이 첫 부팅 시 `api_log` 테이블 생성.

!!! tip "운영 환경 스키마 전략"
    BUILTIN은 빨리 시작하거나 별도 마이그레이션 도구가 없는 프로젝트에 좋습니다. 마이그레이션 추적(Flyway)이나 라이브러리/스키마 엄격 분리(NONE)가 필요하면 [스키마 관리](installation.md#schema-management) 참고.

!!! tip "자체 RestClient 사용"
    이미 특정 대상에 맞춰 설정한 `org.springframework.web.client.RestClient` 빈이 있다면 `@Bean RestClient apiLogRestClient(...)`로 노출하면 `RestApiClientUtil`이 이를 가져옵니다. 없으면 기본 `RestClient.create()`가 사용됩니다.

## 2. `RestApiClientUtil` 주입

```java title="UserService.java"
package com.example.demo;

import kr.devslab.apilog.util.RestApiClientUtil;
import kr.devslab.apilog.model.dto.ApiResponse;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final RestApiClientUtil api;

    public UserService(RestApiClientUtil api) {
        this.api = api;
    }

    public ApiResponse fetchUser(long id) {
        // 동기 GET. HTTP 응답이 도착하면 즉시 리턴.
        // 로깅은 이 메서드가 리턴한 뒤 비동기로 처리됨.
        return api.getSync("/users/" + id);
    }
}
```

## 3. 호출 실행

```java title="DemoApplication.java"
@SpringBootApplication
public class DemoApplication implements CommandLineRunner {

    private final UserService userService;

    public DemoApplication(UserService userService) {
        this.userService = userService;
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Override
    public void run(String... args) {
        ApiResponse response = userService.fetchUser(1);
        System.out.println("Status: " + response.getStatusCode());
    }
}
```

앱 실행:

```bash
./mvnw spring-boot:run
```

## 4. 로그 확인

호출이 끝난 뒤 (비동기 쓰기가 마무리될 시간을 잠깐 주고) `api_log`를 조회:

```sql
SELECT id, event_type, request_id, endpoint, status_code, timestamp
FROM api_log
ORDER BY id DESC
LIMIT 5;
```

한 번의 호출에 두 행:

```text
 id | event_type | request_id                           | endpoint  | status_code | timestamp
----+------------+--------------------------------------+-----------+-------------+---------------------
  2 | SUCCESS    | 2c8f9e1d-7c5a-4f3b-b8a2-d6e0f4a3b1c9 | /users/1  |         200 | 2026-05-18 10:02:14
  1 | INITIATED  | 2c8f9e1d-7c5a-4f3b-b8a2-d6e0f4a3b1c9 | /users/1  |             | 2026-05-18 10:02:14
```

`request_id`로 두 행이 연결됩니다 — 한 호출의 전체 라이프사이클을 재구성하는 방법입니다.

## 5. 페이로드 들여다보기

응답 본문은 JSONB로 보존됩니다:

```sql
SELECT response
FROM api_log
WHERE event_type = 'SUCCESS'
  AND endpoint = '/users/1'
ORDER BY id DESC
LIMIT 1;
```

```json
{
  "data": "{\"id\":1,\"name\":\"Ada Lovelace\",\"email\":\"ada@example.com\"}",
  "statusCode": 200
}
```

특정 필드만 SQL에서 직접 추출:

```sql
SELECT response -> 'data' ->> 'name' AS user_name
FROM api_log
WHERE endpoint = '/users/1' AND event_type = 'SUCCESS'
ORDER BY id DESC LIMIT 1;
```

## 방금 무슨 일이 일어났나

```
UserService#fetchUser(1)
   ↓
RestApiClientUtil#getSync("/users/1")
   ↓ ApiCallInitiatedEvent 발행              → ApiEventListener → INSERT INITIATED 행
   ↓ HTTP GET (동기, 응답 리턴)
   ↓ ApiCallSuccessEvent 발행                → ApiEventListener → INSERT SUCCESS 행
   ↓
ApiResponse 반환
```

두 번의 `publishEvent`는 동기로 실행되지만 `ApiEventListener`는 `@Async`로 처리되어 — 실제 DB INSERT는 백그라운드 executor에서 실행됩니다. 호출자는 로그 INSERT를 절대 기다리지 않습니다.

## 다음 단계

- [`RestApiClientUtil` 사용하기](../guides/using-restapiclient.md) — 전체 메서드 레퍼런스 (`postSync`, `postAsync`, 타입 응답, 커스텀 헤더)
- [이벤트 직접 발행](../guides/publishing-events.md) — 이미 HTTP 클라이언트가 있다면 이벤트만 발행하는 방법
- [재시도 처리](../guides/retry-handling.md) — `RETRY_ERROR` 행과 재시도 타임라인
- [로그 조회](../guides/querying-logs.md) — 에러율, 지연 분포, 벤더 활동 분석 레시피
