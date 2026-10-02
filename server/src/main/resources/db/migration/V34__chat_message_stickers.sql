ALTER TABLE chat_messages
    ADD COLUMN message_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN sticker_code VARCHAR(100);

UPDATE chat_messages
SET message_type = 'STICKER', sticker_code = body
WHERE body LIKE '[bookey:%:%]';

ALTER TABLE chat_messages
    ADD CONSTRAINT chk_chat_message_type
        CHECK (message_type IN ('TEXT', 'STICKER')),
    ADD CONSTRAINT chk_chat_sticker_code
        CHECK (
            (message_type = 'TEXT' AND sticker_code IS NULL)
            OR (message_type = 'STICKER' AND sticker_code IS NOT NULL)
        );
