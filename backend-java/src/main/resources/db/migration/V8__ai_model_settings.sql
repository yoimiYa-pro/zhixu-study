CREATE TABLE ai_model_settings (
  singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
  provider_id varchar(64),
  model_name varchar(200),
  revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
  updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO ai_model_settings(singleton) VALUES (true);
