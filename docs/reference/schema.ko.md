# 스키마

스타터는 테이블 하나를 사용합니다: `api_log`. v0.2.0부터 **테이블은 기본적으로 자동 생성되지 않습니다** — [`api.log.schema.management`](configuration.md#_2) 와 [설치 / 스키마 관리](../getting-started/installation.md#schema-management) 참고 (DDL 직접 적용 vs 번들 Flyway 옵트인).

아래 SQL은 JAR 내부 `classpath:db/api-log/V1.0__create_api_log.sql`의 내용 그대로입니다 — 본인 마이그레이션에 복사해 넣거나 `psql`로 직접 적용 가능합니다.

## 테이블 정의

```sql
CREATE TABLE api_log (
    id            BIGSERIAL    PRIMARY KEY,
    event_type    VARCHAR(50)  NOT NULL,
    request_id    VARCHAR(36)  NOT NULL,
    endpoint      VARCHAR(255) NOT NULL,
    payload       JSONB,
    response      JSONB,
    status_code   INT,
    error_message JSONB,
    timestamp     TIMESTAMP    NOT NULL,
    retry_count   INT          DEFAULT 0,
    is_retry      BOOLEAN      DEFAULT FALSE
);

CREATE INDEX idx_request_id ON api_log (request_id);
CREATE INDEX idx_timestamp  ON api_log (timestamp);
```

## 컬럼

| 컬럼 | 타입 | NULL 허용 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | 불가 | 기본키. INSERT 사이에 단조 증가 — 한 `request_id` 내 정렬에 사용. |
| `event_type` | `VARCHAR(50)` | 불가 | `INITIATED`, `SUCCESS`, `ERROR`, `RETRY_ERROR` 중 하나. [이벤트](events.md) 참고. |
| `request_id` | `VARCHAR(36)` | 불가 | `RestApiClientUtil`이 생성하는 UUID (또는 이벤트 발행자). 한 논리적 호출의 행들을 연결. |
| `endpoint` | `VARCHAR(255)` | 불가 | URL 경로 또는 호출 대상. 예: `/api/users/1`, `https://vendor.com/charge`, `grpc:UserService/Get`. |
| `payload` | `JSONB` | 허용 | 요청 본문. 소스가 JSON이면 JSONB로 저장, 아니면 문자열로 감싸짐. 본문이 없으면 null. |
| `response` | `JSONB` | 허용 | 응답 본문. `INITIATED`, `ERROR`, `RETRY_ERROR` 시 null. |
| `status_code` | `INT` | 허용 | HTTP 상태. `INITIATED`이거나 응답 전 에러(연결 거부, 타임아웃)일 때 null. 업스트림이 무언가 응답하면 설정됨. |
| `error_message` | `JSONB` | 허용 | `ERROR` / `RETRY_ERROR`에서 구조화된 에러. 형식: `{"type": "java.net.SocketTimeoutException", "message": "Read timed out"}`. |
| `timestamp` | `TIMESTAMP` | 불가 | 리스너가 행을 쓴 시각. 이벤트 생성 시점이 아닌 서버 사이드 — 정확한 요청 지연이 필요하면 페이로드에 직접 기록. |
| `retry_count` | `INT` | 허용, 기본 `0` | 시도 번호. 첫 호출은 `0`, 재시도마다 `1+`. |
| `is_retry` | `BOOLEAN` | 허용, 기본 `false` | 재시도 행에서 `true`. 재시도 위주 쿼리에 편리한 술어. |

## 번들 인덱스

| 인덱스 | 컬럼 | 용도 |
|---|---|---|
| `idx_request_id` | `(request_id)` | 한 호출의 타임라인 조회 |
| `idx_timestamp`  | `(timestamp)`  | 시간 범위 필터 (최근 1시간, 24시간 쿼리) |

## 프로덕션에서 추가할 만한 인덱스

마이그레이션은 의도적으로 최소만 제공. 필요해지면 추가:

```sql
-- "엔드포인트별 group by" 대시보드 쿼리 전부에 사용됨.
CREATE INDEX idx_api_log_endpoint ON api_log (endpoint);

-- 에러율 알람과 이벤트 타입 카운트에 사용.
CREATE INDEX idx_api_log_event_type ON api_log (event_type);

-- JSONB 포함 / 존재 연산자 (@>, ?, ?&, ?|) 사용 시 필수.
CREATE INDEX idx_api_log_payload_gin  ON api_log USING GIN (payload);
CREATE INDEX idx_api_log_response_gin ON api_log USING GIN (response);
CREATE INDEX idx_api_log_error_gin    ON api_log USING GIN (error_message);

-- "최근 1시간 에러" — 흔한 대시보드 패턴에 적합.
CREATE INDEX idx_api_log_event_ts ON api_log (event_type, timestamp DESC);
```

## JSONB 형식

컬럼이 JSONB라 정확한 형식은 유연하지만 `RestApiClientUtil`은 일관된 구조로 씁니다:

### `payload`

요청 본문 그대로, Jackson으로 직렬화. `{"name": "Ada", "email": "ada@example.com"}` DTO를 POST했다면 `payload`도 그대로.

평문 문자열 페이로드는 감싸짐: `{"data": "..."}` — `ApiLogService`가 변환.

### `response`

```json
{
  "data": "{\"id\":1,\"name\":\"Ada\"}",
  "statusCode": 200
}
```

`data`는 응답 본문 문자열 (`(response -> 'data')::jsonb`로 다시 파싱 가능). `statusCode`는 최상위 `status_code` 컬럼을 미러링 — JSONB 쿼리 내부에서 편리.

### `error_message`

```json
{
  "type": "org.springframework.web.client.HttpClientErrorException",
  "message": "404 Not Found: [{\"error\":\"user not found\"}]"
}
```

`error_message ->> 'type'` 또는 `error_message ->> 'message'`로 조회.

## 같이 보기

- [설정 레퍼런스](configuration.md) — `api.log.*` 속성
- [이벤트 레퍼런스](events.md) — 이벤트 타입과 `event_type` 매핑
- [로그 조회 가이드](../guides/querying-logs.md) — 실용적인 쿼리 패턴
