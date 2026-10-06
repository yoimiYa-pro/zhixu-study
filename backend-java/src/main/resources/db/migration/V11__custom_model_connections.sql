CREATE TABLE ai_model_connections (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider varchar(100) NOT NULL,
  base_url varchar(2048) NOT NULL,
  model_name varchar(200) NOT NULL,
  api_key_ciphertext text NOT NULL,
  json_mode boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE ai_model_settings ADD COLUMN connection_id uuid REFERENCES ai_model_connections(id);
ALTER TABLE ai_model_settings ADD CONSTRAINT selected_model_source CHECK
  (connection_id IS NULL OR (provider_id IS NULL AND model_name IS NULL));
