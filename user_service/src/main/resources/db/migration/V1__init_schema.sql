CREATE TABLE public.blocks (
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    blocked_id uuid NOT NULL,
    blocker_id uuid NOT NULL,
    created_by uuid,
    deleted_by uuid,
    id uuid NOT NULL,
    updated_by uuid
);
CREATE TABLE public.follows (
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_by uuid,
    follower_id uuid NOT NULL,
    following_id uuid NOT NULL,
    id uuid NOT NULL,
    updated_by uuid
);
CREATE TABLE public.reports (
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_by uuid,
    id uuid NOT NULL,
    reported_id uuid NOT NULL,
    reporter_id uuid NOT NULL,
    updated_by uuid,
    report_type character varying(30) NOT NULL,
    content text,
    CONSTRAINT reports_report_type_check CHECK (((report_type)::text = ANY ((ARRAY['SPAM'::character varying, 'HATE_SPEECH'::character varying, 'SEXUAL_CONTENT'::character varying, 'VIOLENCE'::character varying, 'MISINFORMATION'::character varying, 'OTHER'::character varying])::text[])))
);
CREATE TABLE public.users (
    birthdate date,
    interest_registered boolean NOT NULL,
    latitude double precision,
    longitude double precision,
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    last_login_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    gender character varying(20) NOT NULL,
    login_provider character varying(20) NOT NULL,
    role character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    nickname character varying(50) NOT NULL,
    neighborhood character varying(100),
    bio character varying(200),
    profile_image_url character varying(500),
    email character varying(255) NOT NULL,
    password character varying(255),
    CONSTRAINT users_gender_check CHECK (((gender)::text = ANY ((ARRAY['MALE'::character varying, 'FEMALE'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT users_login_provider_check CHECK (((login_provider)::text = ANY ((ARRAY['EMAIL'::character varying, 'KAKAO'::character varying, 'GOOGLE'::character varying])::text[]))),
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'BANNED'::character varying])::text[])))
);
ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT blocks_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.follows
    ADD CONSTRAINT follows_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT uq_blocks_blocker_blocked UNIQUE (blocker_id, blocked_id);
ALTER TABLE ONLY public.follows
    ADD CONSTRAINT uq_follows_follower_following UNIQUE (follower_id, following_id);
ALTER TABLE ONLY public.reports
    ADD CONSTRAINT uq_reports_reporter_reported UNIQUE (reporter_id, reported_id);
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_nickname_key UNIQUE (nickname);
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);
CREATE INDEX idx_blocks_blocked_id ON public.blocks USING btree (blocked_id);
CREATE INDEX idx_blocks_blocker_id ON public.blocks USING btree (blocker_id);
CREATE INDEX idx_follows_follower_id ON public.follows USING btree (follower_id);
CREATE INDEX idx_follows_following_id ON public.follows USING btree (following_id);
CREATE INDEX idx_reports_reported_id ON public.reports USING btree (reported_id);
CREATE INDEX idx_reports_reporter_id ON public.reports USING btree (reporter_id);
CREATE INDEX idx_users_email ON public.users USING btree (email);
CREATE INDEX idx_users_nickname ON public.users USING btree (nickname);
ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fkd3qiw2om5d2oh5xb7fbdcq225 FOREIGN KEY (reporter_id) REFERENCES public.users(id);
ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT fkjcracc6rem4ddb6gtkaxhfsvu FOREIGN KEY (blocked_id) REFERENCES public.users(id);
ALTER TABLE ONLY public.blocks
    ADD CONSTRAINT fkl05bxnwmimx5n5tsgrgnguvu6 FOREIGN KEY (blocker_id) REFERENCES public.users(id);
ALTER TABLE ONLY public.follows
    ADD CONSTRAINT fkonkdkae2ngtx70jqhsh7ol6uq FOREIGN KEY (following_id) REFERENCES public.users(id);
ALTER TABLE ONLY public.follows
    ADD CONSTRAINT fkqnkw0cwwh6572nyhvdjqlr163 FOREIGN KEY (follower_id) REFERENCES public.users(id);
ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fks5hp3xww9pl0bwblxl3cfwue2 FOREIGN KEY (reported_id) REFERENCES public.users(id);