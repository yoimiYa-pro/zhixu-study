ALTER TABLE essay_materials ADD COLUMN revision integer NOT NULL DEFAULT 1;
CREATE UNIQUE INDEX essay_materials_news_title_unique ON essay_materials(current_affair_id,title);
