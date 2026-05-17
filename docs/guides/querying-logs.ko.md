# 로그 조회

모든 API 호출은 `api_log`에 한 행 또는 여러 행으로 기록되며, 본문은 JSONB로 보존됩니다. 이슈 분석이나 대시보드 구축 시 자주 쓰는 패턴들입니다.

## 효과 있는 인덱스

무거운 쿼리 전에 다음 인덱스가 있는지 확인 (앞의 두 개는 번들 마이그레이션이 만들어 주고, 나머지는 필요할 때):

```sql
-- 스타터가 만들어주는 것:
CREATE INDEX idx_request_id ON api_log (request_id);
CREATE INDEX idx_timestamp  ON api_log (timestamp);

-- 테이블이 커지면 직접 마이그레이션으로 추가:
CREATE INDEX idx_api_log_endpoint    ON api_log (endpoint);
CREATE INDEX idx_api_log_event_type  ON api_log (event_type);
CREATE INDEX idx_api_log_payload_gin  ON api_log USING GIN (payload);
CREATE INDEX idx_api_log_response_gin ON api_log USING GIN (response);
CREATE INDEX idx_api_log_error_gin    ON api_log USING GIN (error_message);
```

GIN 인덱스가 있어야 JSONB의 `?`, `?&`, `?|`, `@>`, `<@` 연산자가 빠릅니다.

## 한 호출의 타임라인 복원

`request_id`를 알면 (호출 지점에서 로깅했거나 벤더 에러에 들어있다면) 모든 행을 가져올 수 있습니다:

```sql
SELECT id, event_type, retry_count, is_retry, status_code, timestamp,
       payload, response, error_message
FROM api_log
WHERE request_id = '2c8f9e1d-7c5a-4f3b-b8a2-d6e0f4a3b1c9'
ORDER BY id;
```

행 순서가 실제 시간 순서입니다 — `id`는 `BIGSERIAL`로 INSERT 사이에 단조 증가.

## 엔드포인트별 최근 트래픽

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

## 에러율 알람

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
WHERE errors::numeric / terminals > 0.05   -- 5% 이상
ORDER BY error_pct DESC;
```

Grafana 패널이나 크론 기반 Slack 알람에 넣으면 — 임계값 초과 시 알람.

## `INITIATED`에서 `SUCCESS`까지의 지연

두 행이 `request_id`를 공유하므로 호출 시간을 계산할 수 있습니다:

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

`timestamp` 컬럼은 서버 사이드 (리스너가 행을 쓴 시각)이므로 비동기 파이프라인 오버헤드가 포함됩니다. 정확한 지연이 필요하면 응답 페이로드에 직접 기록.

## JSONB 본문 검색

**요청에 특정 사용자가 언급된 호출 모두:**

```sql
SELECT timestamp, endpoint, status_code, response -> 'data' AS data
FROM api_log
WHERE payload @> '{"userId": 12345}'::jsonb
  AND event_type = 'SUCCESS'
ORDER BY timestamp DESC
LIMIT 50;
```

**특정 메시지 부분 문자열을 포함하는 에러:**

```sql
SELECT timestamp, endpoint, error_message ->> 'message' AS message
FROM api_log
WHERE event_type = 'ERROR'
  AND error_message ->> 'message' ILIKE '%timeout%'
ORDER BY timestamp DESC;
```

**깊게 중첩된 필드 추출:**

```sql
SELECT timestamp, endpoint,
       response -> 'data' -> 'metadata' ->> 'requestId' AS upstream_request_id
FROM api_log
WHERE event_type = 'SUCCESS'
  AND endpoint = '/vendor/charges';
```

## 보존 — 테이블 건강 유지

테이블은 계속 커집니다. Devslab에서는 보통 월별로 파티션하고 최근 N개월만 핫하게 유지, 그 외는 S3나 별도 Postgres 인스턴스로 아카이브. 매일 밤 돌릴 수 있는 최소 정리:

```sql
-- 90일 이상 된 로그 행 삭제
DELETE FROM api_log WHERE timestamp < NOW() - INTERVAL '90 days';

-- 또는 요약 통계만 유지하고 싶을 때:
INSERT INTO api_log_daily_summary (date, endpoint, calls, errors)
SELECT DATE(timestamp), endpoint,
       COUNT(*) FILTER (WHERE event_type = 'INITIATED'),
       COUNT(*) FILTER (WHERE event_type IN ('ERROR','RETRY_ERROR'))
FROM api_log
WHERE timestamp < NOW() - INTERVAL '90 days'
GROUP BY DATE(timestamp), endpoint;
```

트래픽이 많은 서비스라면 Postgres 네이티브 파티셔닝(`PARTITION BY RANGE (timestamp)`)을 고려하세요 — 파티션 drop은 O(1), `DELETE`보다 훨씬 빠릅니다.

## 같이 보기

- [레퍼런스 / 스키마](../reference/schema.md) — 전체 컬럼 + JSONB 구조
- [재시도 처리](retry-handling.md) — 재시도 위주 쿼리
