ALTER TABLE chat_messages ADD COLUMN retrieval_status varchar(30) NOT NULL DEFAULT 'DATABASE_ONLY';
