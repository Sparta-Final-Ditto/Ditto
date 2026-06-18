CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE chat_rooms (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    room_type varchar(20) NOT NULL,
    room_name varchar(100),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_by uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL DEFAULT now(),
    updated_at timestamp(6) with time zone NOT NULL DEFAULT now(),
    last_message_id varchar(50),
    last_message_at timestamp(6) with time zone,
    inactivated_at timestamp(6) with time zone,
    inactivated_by uuid
);

CREATE INDEX idx_chat_rooms_last_message_at
    ON chat_rooms (last_message_at);

CREATE TABLE chat_room_participants (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role varchar(20) NOT NULL DEFAULT 'MEMBER',
    joined_at timestamp(6) with time zone NOT NULL DEFAULT now(),
    left_at timestamp(6) with time zone,
    last_read_message_id varchar(50),
    last_read_at timestamp(6) with time zone,
    last_visible_message_id varchar(50),
    is_hidden boolean NOT NULL DEFAULT false,
    notification_enabled boolean NOT NULL DEFAULT true,
    CONSTRAINT fk_chat_room_participants_room_id
        FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
    CONSTRAINT uk_chat_room_participants_room_id_user_id
        UNIQUE (room_id, user_id)
);

CREATE INDEX idx_chat_room_participants_user_id_left_at
    ON chat_room_participants (user_id, left_at);

CREATE INDEX idx_chat_room_participants_room_id_left_at
    ON chat_room_participants (room_id, left_at);

CREATE TABLE direct_chat_pairs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id uuid NOT NULL,
    user1_id uuid NOT NULL,
    user2_id uuid NOT NULL,
    CONSTRAINT fk_direct_chat_pairs_room_id
        FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
    CONSTRAINT uk_direct_chat_pairs_room_id
        UNIQUE (room_id),
    CONSTRAINT uk_direct_chat_pairs_user1_id_user2_id
        UNIQUE (user1_id, user2_id)
);
