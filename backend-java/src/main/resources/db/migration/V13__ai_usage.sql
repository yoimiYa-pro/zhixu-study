CREATE TABLE ai_usage (
    id uuid PRIMARY KEY,
    provider_key varchar(64) NOT NULL,
    provider_name varchar(253) NOT NULL,
    model varchar(200) NOT NULL,
    kind varchar(16) NOT NULL CHECK (kind IN ('CHAT','EMBEDDING')),
    calls bigint NOT NULL CHECK (calls > 0),
    reported_calls bigint NOT NULL CHECK (reported_calls BETWEEN 0 AND calls),
    incomplete_calls bigint NOT NULL CHECK (incomplete_calls BETWEEN 0 AND calls),
    cache_reported_calls bigint NOT NULL CHECK (cache_reported_calls BETWEEN 0 AND calls),
    input_tokens bigint NOT NULL CHECK (input_tokens >= 0),
    output_tokens bigint NOT NULL CHECK (output_tokens >= 0),
    total_tokens bigint NOT NULL CHECK (total_tokens >= 0),
    cached_input_tokens bigint NOT NULL CHECK (cached_input_tokens BETWEEN 0 AND input_tokens),
    recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ai_usage_recorded_at_idx ON ai_usage(recorded_at);
