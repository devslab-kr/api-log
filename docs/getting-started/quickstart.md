# Quickstart

A 5-minute walkthrough: make an outbound HTTP call, verify the rows land in `api_log`, query them back.

## 1. Configure a base URL

`RestApiClientUtil` uses a single `RestClient` instance with a configured base URL. Set it on your application config:

```yaml title="application.yml"
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/your_db
    username: your_user
    password: your_password

api:
  log:
    enabled: true
    schema:
      management: flyway   # let the starter create the api_log table for this quickstart
                           # (default is 'none' — you apply the DDL yourself; see Installation)
```

!!! note "Flyway required for this quickstart"
    `schema.management=flyway` needs Flyway on the classpath. Add it:
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
    Or, for production setups, leave `management: none` and apply [the DDL](../reference/schema.md) via your own migration tool.

!!! tip "Bring your own RestClient"
    If you already have an `org.springframework.web.client.RestClient` bean configured for a specific target, expose it as `@Bean RestClient apiLogRestClient(...)` and `RestApiClientUtil` will pick it up. Otherwise the default uses Spring's `RestClient.create()`.

## 2. Inject `RestApiClientUtil`

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
        // Synchronous GET. Returns once the HTTP response is back.
        // Logging happens asynchronously after this returns.
        return api.getSync("/users/" + id);
    }
}
```

## 3. Make a call

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

Start the app:

```bash
./mvnw spring-boot:run
```

## 4. Inspect the logs

After the call returns (give it a beat for the async write to settle), query `api_log`:

```sql
SELECT id, event_type, request_id, endpoint, status_code, timestamp
FROM api_log
ORDER BY id DESC
LIMIT 5;
```

Two rows for that one call:

```text
 id | event_type | request_id                           | endpoint  | status_code | timestamp
----+------------+--------------------------------------+-----------+-------------+---------------------
  2 | SUCCESS    | 2c8f9e1d-7c5a-4f3b-b8a2-d6e0f4a3b1c9 | /users/1  |         200 | 2026-05-18 10:02:14
  1 | INITIATED  | 2c8f9e1d-7c5a-4f3b-b8a2-d6e0f4a3b1c9 | /users/1  |             | 2026-05-18 10:02:14
```

The `request_id` correlates both rows — that's how you reconstruct the full lifecycle of one outbound call.

## 5. Look at the payload

The response body is preserved as JSONB. Pull it back as JSON:

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

Or extract a specific field directly in SQL:

```sql
SELECT response -> 'data' ->> 'name' AS user_name
FROM api_log
WHERE endpoint = '/users/1' AND event_type = 'SUCCESS'
ORDER BY id DESC LIMIT 1;
```

## What just happened

```
UserService#fetchUser(1)
   ↓
RestApiClientUtil#getSync("/users/1")
   ↓ publishes ApiCallInitiatedEvent          → ApiEventListener → INSERT INITIATED row
   ↓ HTTP GET (synchronous, returns response)
   ↓ publishes ApiCallSuccessEvent            → ApiEventListener → INSERT SUCCESS row
   ↓
returns ApiResponse to your code
```

The two `publishEvent` calls happen synchronously, but `ApiEventListener` is annotated `@Async` — the actual database writes execute on a background executor. The caller never blocks on a log INSERT.

## Where to go next

- [Using `RestApiClientUtil`](../guides/using-restapiclient.md) — full method reference (`postSync`, `postAsync`, typed responses, custom headers)
- [Publishing events manually](../guides/publishing-events.md) — already have an HTTP client? Just emit the events
- [Retry handling](../guides/retry-handling.md) — `RETRY_ERROR` rows and the retry timeline
- [Querying logs](../guides/querying-logs.md) — recipes for error rates, latency distribution, vendor activity
