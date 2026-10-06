CREATE TABLE chat_conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title varchar(80) NOT NULL DEFAULT '新聊天' CHECK (length(trim(title)) > 0),
    title_manual boolean NOT NULL DEFAULT false,
    question_id uuid REFERENCES questions(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    active_turn_id uuid,
    active_request_id uuid,
    active_until timestamptz,
    CHECK ((active_turn_id IS NULL AND active_request_id IS NULL AND active_until IS NULL)
        OR (active_turn_id IS NOT NULL AND active_request_id IS NOT NULL AND active_until IS NOT NULL))
);
CREATE INDEX chat_conversations_recent_idx ON chat_conversations(updated_at DESC, id DESC);
ALTER TABLE chat_messages ADD COLUMN conversation_id uuid;

-- Preserve every message, identifier, citation and timestamp from the old chat.
DO $$
DECLARE legacy_id uuid;
BEGIN
    IF EXISTS (SELECT 1 FROM chat_messages) THEN
        INSERT INTO chat_conversations(title, created_at, updated_at)
        SELECT '此前的学习对话', min(created_at), max(created_at) FROM chat_messages
        RETURNING id INTO legacy_id;
        UPDATE chat_messages SET conversation_id = legacy_id;
    END IF;
END $$;

ALTER TABLE chat_messages ALTER COLUMN conversation_id SET NOT NULL;
ALTER TABLE chat_messages ADD CONSTRAINT chat_messages_conversation_fk
    FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE;
CREATE INDEX chat_messages_conversation_idx ON chat_messages(conversation_id, created_at DESC, id DESC);
