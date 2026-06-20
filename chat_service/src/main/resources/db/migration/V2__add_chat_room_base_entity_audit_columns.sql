ALTER TABLE chat_rooms
    ADD COLUMN updated_by uuid,
    ADD COLUMN deleted_at timestamp(6) with time zone,
    ADD COLUMN deleted_by uuid;
