CREATE TABLE api_log
(
    id            BIGSERIAL PRIMARY KEY,
    event_type    VARCHAR(50)  NOT NULL,
    request_id    VARCHAR(36)  NOT NULL,
    endpoint      VARCHAR(255) NOT NULL,
    payload       JSONB,
    response      JSONB,
    status_code   INT,
    error_message JSONB,
    timestamp     TIMESTAMP    NOT NULL,
    retry_count   INT     DEFAULT 0,
    is_retry      BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_request_id ON api_log (request_id);
CREATE INDEX idx_timestamp ON api_log (timestamp);