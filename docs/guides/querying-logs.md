# Querying logs

Every API call lands in `api_log` as one or more rows with bodies preserved as JSONB. Here are the patterns that come up most often when investigating issues or building dashboards.

## Indexes that pay off

Before running heavy queries, make sure these indexes exist (the bundled migration creates the first two; the rest are at your discretion):

```sql
-- Shipped with the starter:
CREATE INDEX idx_request_id ON api_log (request_id);
CREATE INDEX idx_timestamp  ON api_log (timestamp);

-- Add these in your own migration when the table grows:
CREATE INDEX idx_api_log_endpoint    ON api_log (endpoint);
CREATE INDEX idx_api_log_event_type  ON api_log (event_type);
CREATE INDEX idx_api_log_payload_gin  ON api_log USING GIN (payload);
CREATE INDEX idx_api_log_response_gin ON api_log USING GIN (response);
CREATE INDEX idx_api_log_error_gin    ON api_log USING GIN (error_message);
```

GIN indexes light up `?`, `?&`, `?|`, `@>`, `<@` operators on JSONB.

## Reconstructing a single call's timeline

If you know the `request_id` (logged at the call site or returned in a vendor error), pull every row for that call:

```sql
SELECT id, event_type, retry_count, is_retry, status_code, timestamp,
       payload, response, error_message
FROM api_log
WHERE request_id = '2c8f9e1d-7c5a-4f3b-b8a2-d6e0f4a3b1c9'
ORDER BY id;
```

The row order is the actual chronological order — `id` is `BIGSERIAL`, monotonic across inserts.

## Recent traffic per endpoint

```sql
SELECT endpoint,
       COUNT(*) FILTER (WHERE event_type = 'INITIATED') AS calls,
       COUNT(*) FILTER (WHERE event_type = 'SUCCESS')   AS ok,
       COUNT(*) FILTER (WHERE event_type = 'ERROR')     AS failed,
       COUNT(*) FILTER (WHERE event_type = 'RETRY_ERROR') AS retried
FROM api_log
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY endpoint
ORDER BY calls DESC;
```

## Error rate alert

```sql
WITH stats AS (
    SELECT endpoint,
           COUNT(*) FILTER (WHERE event_type = 'ERROR')   AS errors,
           COUNT(*) FILTER (WHERE event_type IN ('SUCCESS','ERROR')) AS terminals
    FROM api_log
    WHERE timestamp > NOW() - INTERVAL '15 minutes'
    GROUP BY endpoint
    HAVING COUNT(*) FILTER (WHERE event_type IN ('SUCCESS','ERROR')) > 10
)
SELECT endpoint, errors, terminals,
       ROUND(errors::numeric / terminals * 100, 1) AS error_pct
FROM stats
WHERE errors::numeric / terminals > 0.05   -- > 5%
ORDER BY error_pct DESC;
```

Throw this in a Grafana panel or a cron-driven Slack notification — anything above the threshold is your alert.

## Latency from `INITIATED` to `SUCCESS`

Since both rows share `request_id`, you can compute call duration:

```sql
WITH paired AS (
    SELECT i.request_id, i.endpoint,
           i.timestamp AS started_at,
           s.timestamp AS finished_at,
           EXTRACT(EPOCH FROM (s.timestamp - i.timestamp)) * 1000 AS duration_ms
    FROM api_log i
    JOIN api_log s
      ON i.request_id = s.request_id
     AND i.event_type = 'INITIATED'
     AND s.event_type = 'SUCCESS'
    WHERE i.timestamp > NOW() - INTERVAL '1 hour'
)
SELECT endpoint,
       COUNT(*)                      AS calls,
       AVG(duration_ms)::int         AS avg_ms,
       PERCENTILE_CONT(0.5)  WITHIN GROUP (ORDER BY duration_ms)::int AS p50_ms,
       PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms)::int AS p95_ms,
       PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY duration_ms)::int AS p99_ms
FROM paired
GROUP BY endpoint
ORDER BY p95_ms DESC;
```

The `timestamp` column is server-side (when the listener wrote the row), so the durations include some async pipeline overhead. For accurate latency, log it explicitly in the response payload.

## Searching inside JSONB bodies

**Find all calls where the request mentioned a specific user:**

```sql
SELECT timestamp, endpoint, status_code, response -> 'data' AS data
FROM api_log
WHERE payload @> '{"userId": 12345}'::jsonb
  AND event_type = 'SUCCESS'
ORDER BY timestamp DESC
LIMIT 50;
```

**Errors containing a specific message substring:**

```sql
SELECT timestamp, endpoint, error_message ->> 'message' AS message
FROM api_log
WHERE event_type = 'ERROR'
  AND error_message ->> 'message' ILIKE '%timeout%'
ORDER BY timestamp DESC;
```

**Pull a deep nested field:**

```sql
SELECT timestamp, endpoint,
       response -> 'data' -> 'metadata' ->> 'requestId' AS upstream_request_id
FROM api_log
WHERE event_type = 'SUCCESS'
  AND endpoint = '/vendor/charges';
```

## Retention — keeping the table healthy

The table grows. At Devslab we typically partition by month and keep the last N months hot, archiving older ones to S3 or a separate Postgres instance. A minimal cleanup that you can run nightly:

```sql
-- Delete log rows older than 90 days
DELETE FROM api_log WHERE timestamp < NOW() - INTERVAL '90 days';

-- Or, if you want to keep summary stats:
INSERT INTO api_log_daily_summary (date, endpoint, calls, errors)
SELECT DATE(timestamp), endpoint,
       COUNT(*) FILTER (WHERE event_type = 'INITIATED'),
       COUNT(*) FILTER (WHERE event_type IN ('ERROR','RETRY_ERROR'))
FROM api_log
WHERE timestamp < NOW() - INTERVAL '90 days'
GROUP BY DATE(timestamp), endpoint;
```

For high-traffic services, look into Postgres native partitioning (`PARTITION BY RANGE (timestamp)`) — drop a partition is O(1), much faster than `DELETE`.

## See also

- [Reference / Schema](../reference/schema.md) — full column list + JSONB shapes
- [Retry handling](retry-handling.md) — queries focused on retries
