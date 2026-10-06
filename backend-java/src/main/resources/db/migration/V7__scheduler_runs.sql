CREATE TABLE scheduler_runs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  kind varchar(40) NOT NULL, run_date date NOT NULL,
  status varchar(20) NOT NULL CHECK(status IN ('COMPLETED','FAILED')),
  result_json jsonb, error_code varchar(100), ran_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(kind,run_date)
);
