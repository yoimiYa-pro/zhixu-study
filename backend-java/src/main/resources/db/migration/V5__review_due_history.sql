ALTER TABLE reviews ADD COLUMN due_at timestamptz;
CREATE INDEX reviews_due_at_idx ON reviews(due_at);
