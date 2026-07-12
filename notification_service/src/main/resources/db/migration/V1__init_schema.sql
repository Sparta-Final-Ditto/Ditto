CREATE TABLE public.notifications (
    is_read boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    actor_id uuid NOT NULL,
    created_by uuid,
    deleted_by uuid,
    id uuid NOT NULL,
    receiver_id uuid NOT NULL,
    updated_by uuid,
    message character varying(255) NOT NULL,
    meta_data character varying(255),
    target_id character varying(255) NOT NULL,
    target_type character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT notifications_target_type_check CHECK (((target_type)::text = ANY ((ARRAY['LIKE'::character varying, 'COMMENT'::character varying, 'CHAT_MESSAGE'::character varying])::text[]))),
    CONSTRAINT notifications_type_check CHECK (((type)::text = ANY ((ARRAY['LIKE'::character varying, 'COMMENT'::character varying, 'CHAT_MESSAGE'::character varying])::text[])))
);
ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT uq_notifications_type_target_id_receiver_id UNIQUE (type, target_id, receiver_id);
CREATE INDEX idx_notifications_receiver_id_created_at_id ON public.notifications USING btree (receiver_id, created_at DESC, id DESC);