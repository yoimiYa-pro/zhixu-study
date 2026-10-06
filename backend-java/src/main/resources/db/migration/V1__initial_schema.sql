CREATE TABLE users (
  id uuid PRIMARY KEY,
  username varchar(100) NOT NULL UNIQUE,
  password_hash varchar(100) NOT NULL,
  singleton boolean NOT NULL DEFAULT true UNIQUE CHECK (singleton),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE questions (
  id uuid PRIMARY KEY, content text NOT NULL, correct_answer text NOT NULL,
  user_answer text NOT NULL DEFAULT '', explanation text NOT NULL DEFAULT '',
  question_type varchar(30) NOT NULL,
  difficulty varchar(10) NOT NULL DEFAULT '中等', source varchar(300) NOT NULL DEFAULT '',
  year integer CHECK (year BETWEEN 1980 AND 2100), region varchar(100) NOT NULL DEFAULT '',
  analysis_json jsonb, revision integer NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);
CREATE TABLE question_options (
  question_id uuid NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
  option_key varchar(2) NOT NULL, content text NOT NULL,
  PRIMARY KEY (question_id, option_key)
);
CREATE TABLE mistakes (
  id uuid PRIMARY KEY, question_id uuid NOT NULL UNIQUE REFERENCES questions(id) ON DELETE CASCADE,
  reason varchar(30) NOT NULL DEFAULT '未确认', confirmed boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(), last_review_at timestamptz
);
CREATE TABLE knowledge_points (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name varchar(200) NOT NULL UNIQUE,
  parent_id uuid REFERENCES knowledge_points(id) ON DELETE RESTRICT,
  description text NOT NULL DEFAULT '', created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (id <> parent_id)
);
CREATE TABLE question_knowledge_points (
  question_id uuid NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
  knowledge_point_id uuid NOT NULL REFERENCES knowledge_points(id) ON DELETE RESTRICT,
  PRIMARY KEY(question_id, knowledge_point_id)
);
CREATE TABLE review_plans (
  question_id uuid PRIMARY KEY REFERENCES questions(id) ON DELETE CASCADE,
  stage integer NOT NULL DEFAULT 0 CHECK (stage BETWEEN 0 AND 4),
  attempts integer NOT NULL DEFAULT 0, correct_count integer NOT NULL DEFAULT 0,
  next_review_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX review_plans_due_idx ON review_plans(next_review_at);
CREATE TABLE reviews (
  id uuid PRIMARY KEY, request_id uuid NOT NULL UNIQUE,
  question_id uuid NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
  outcome varchar(10) NOT NULL CHECK(outcome IN ('CORRECT','INCORRECT','SKIP')),
  answer text NOT NULL DEFAULT '', time_spent integer NOT NULL CHECK(time_spent BETWEEN 0 AND 86400),
  confidence integer NOT NULL CHECK(confidence BETWEEN 1 AND 5),
  reviewed_at timestamptz NOT NULL DEFAULT now(), next_review_at timestamptz NOT NULL
);
CREATE TABLE study_records (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  question_id uuid REFERENCES questions(id) ON DELETE SET NULL,
  kind varchar(20) NOT NULL, quantity integer NOT NULL CHECK(quantity >= 0),
  wrong_count integer NOT NULL CHECK(wrong_count BETWEEN 0 AND quantity),
  time_spent integer NOT NULL DEFAULT 0 CHECK(time_spent BETWEEN 0 AND 86400),
  note varchar(1000) NOT NULL DEFAULT '', studied_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX study_records_date_idx ON study_records(studied_at);
CREATE TABLE current_affairs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), title varchar(500) NOT NULL, content text NOT NULL,
  source varchar(200) NOT NULL, source_url text UNIQUE, publish_time timestamptz,
  fetch_time timestamptz NOT NULL DEFAULT now(), source_unverified boolean NOT NULL DEFAULT true,
  analysis_json jsonb, revision integer NOT NULL DEFAULT 1
);
CREATE INDEX current_affairs_date_idx ON current_affairs(fetch_time);
CREATE TABLE idioms (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), word varchar(100) NOT NULL UNIQUE,
  kind varchar(10) NOT NULL DEFAULT '成语', definition text NOT NULL,
  synonyms_json jsonb NOT NULL DEFAULT '[]', antonyms_json jsonb NOT NULL DEFAULT '[]',
  scenario text NOT NULL DEFAULT '', pitfalls text NOT NULL DEFAULT '', example text NOT NULL DEFAULT '',
  favorite boolean NOT NULL DEFAULT false, mastered boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE essay_materials (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), title varchar(300) NOT NULL,
  content text NOT NULL, category varchar(30) NOT NULL, kind varchar(30) NOT NULL,
  tags_json jsonb NOT NULL DEFAULT '[]', source varchar(200) NOT NULL DEFAULT '', source_url text,
  publish_time timestamptz, fetch_time timestamptz NOT NULL DEFAULT now(),
  source_unverified boolean NOT NULL DEFAULT true,
  current_affair_id uuid REFERENCES current_affairs(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE weekly_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), week_start date NOT NULL UNIQUE,
  week_end date NOT NULL, stats_json jsonb NOT NULL, summary_json jsonb,
  revision integer NOT NULL DEFAULT 1, created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ai_tasks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), kind varchar(40) NOT NULL,
  reference_id uuid, payload_json jsonb NOT NULL DEFAULT '{}',
  status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
  attempts integer NOT NULL DEFAULT 0, error_code varchar(100),
  result_json jsonb, available_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(), started_at timestamptz, completed_at timestamptz,
  dedupe_key varchar(200) UNIQUE
);
CREATE INDEX ai_tasks_poll_idx ON ai_tasks(status, available_at, created_at);
CREATE TABLE daily_tasks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), task_date date NOT NULL,
  kind varchar(30) NOT NULL, title varchar(200) NOT NULL, target integer NOT NULL DEFAULT 1,
  completed boolean NOT NULL DEFAULT false, UNIQUE(task_date, kind)
);
CREATE TABLE chat_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), turn_id uuid NOT NULL,
  role varchar(10) NOT NULL CHECK(role IN ('user','assistant')),
  content text NOT NULL, citations_json jsonb NOT NULL DEFAULT '[]',
  created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(turn_id, role)
);
