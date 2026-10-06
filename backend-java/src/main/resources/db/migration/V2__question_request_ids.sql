ALTER TABLE questions ADD COLUMN creation_request uuid UNIQUE;
CREATE INDEX questions_active_idx ON questions(created_at DESC) WHERE deleted_at IS NULL;
