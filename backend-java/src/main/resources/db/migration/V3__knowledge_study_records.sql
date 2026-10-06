ALTER TABLE study_records ADD COLUMN knowledge_point_id uuid REFERENCES knowledge_points(id) ON DELETE SET NULL;
