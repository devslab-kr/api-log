---
title: api-log — Event-driven API call logging for Spring Boot
---

# api-log

> **Event-driven API call logging for Spring Boot.**
> Async event pipeline + PostgreSQL JSONB. Log every outbound call without slowing the request path.

[:fontawesome-solid-rocket: Get Started](getting-started/installation.md){ .md-button .md-button--primary }
[:fontawesome-brands-github: GitHub](https://github.com/devslab-kr/api-log){ .md-button }

---

## At a glance

Drop the starter on your classpath and your service automatically logs every outbound HTTP call — request, response, status code, errors, retries — to PostgreSQL. The HTTP call doesn't wait for the log write; events are dispatched asynchronously.

```java
@Service
public class UserService {

    private final RestApiClientUtil api;

    public UserService(RestApiClientUtil api) {
        this.api = api;
    }

    public User createUser(User newUser) {
        // The HTTP call is synchronous and returns immediately.
        // Three log rows land in api_log behind the scenes: INITIATED, then SUCCESS or ERROR.
        return api.postSyncTyped("/api/users", newUser, User.class);
    }
}
```

A single call writes to the `api_log` table:

```sql
SELECT event_type, endpoint, status_code, timestamp FROM api_log ORDER BY id DESC LIMIT 3;

 event_type | endpoint     | status_code | timestamp
------------+--------------+-------------+----------------------
 SUCCESS    | /api/users   |         201 | 2026-05-18 10:02:14
 INITIATED  | /api/users   |             | 2026-05-18 10:02:14
```

Bodies (`payload`, `response`, `error_message`) are stored as JSONB — queryable with `->`, `->>`, and GIN indexes. No more "we don't know what we sent to that vendor".

## What you get

<div class="grid cards" markdown>

-   :material-flash: **Non-blocking by design**

    Log writes happen on a separate thread via `ApplicationEventPublisher`. Your HTTP call returns the moment the response arrives — the log row is persisted after.

-   :material-database: **PostgreSQL JSONB storage**

    Request bodies, response bodies, and error details are stored as JSONB. Index with GIN, query with `->`, `->>`, `jsonb_path_query`. Schema-less where it matters, structured where you need it.

-   :material-restart: **Retry-aware schema**

    `RETRY_ERROR` event type + `retry_count` / `is_retry` columns let you record every attempt of a flaky call. The listener also retries its own log writes 3× on transient DB failures, so a wobbly connection doesn't lose log rows.

-   :material-rocket-launch: **Virtual Threads ready**

    Designed for Java 21+ async. Compatible with `spring.threads.virtual.enabled=true` — low memory footprint, high concurrency.

-   :material-puzzle: **Drop-in starter**

    Auto-configuration registers `ApiEventListener`, `ApiLogService`, and `RestApiClientUtil` behind `@ConditionalOnMissingBean`. Override any of them by declaring your own.

-   :material-shield-check: **CI-verified**

    Comprehensive test suite covering services, repository, listener, and Testcontainers-backed PostgreSQL integration — runs on every PR and push.

</div>

## When to use this

**Reach for `api-log` when** you need durable, queryable history of outbound API calls — for debugging vendor integrations, compliance audits, billing reconciliation, or producing customer-facing usage reports.

**Reach for something else when** you only need ephemeral observability (use OpenTelemetry + a trace backend) or fire-and-forget transport logs (just log to stdout and ship to a log aggregator).

## Architecture

```
Caller code
   ↓
RestApiClientUtil  (or your own HTTP client)
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

Every call produces at least two rows: `INITIATED` (fired immediately) and a terminal row (`SUCCESS`, `ERROR`, or `RETRY_ERROR`). All four event types correlate via `request_id`, so you can reconstruct the full timeline of any call with a single query.

## Next steps

- [Install the starter](getting-started/installation.md) — Maven and Gradle coordinates
- [Quickstart](getting-started/quickstart.md) — get your first log row in 5 minutes
- [Using `RestApiClientUtil`](guides/using-restapiclient.md) — bundled HTTP client
- [Publishing events manually](guides/publishing-events.md) — bring your own HTTP client
- [Reference / Schema](reference/schema.md) — `api_log` table structure
