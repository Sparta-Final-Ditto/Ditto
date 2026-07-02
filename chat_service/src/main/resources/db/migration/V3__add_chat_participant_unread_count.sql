ALTER TABLE chat_room_participants
    ADD COLUMN unread_count bigint NOT NULL DEFAULT 0;
