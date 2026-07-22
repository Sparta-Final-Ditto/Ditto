CREATE TABLE public.comments (
    deleted_by_post_deletion boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_by uuid,
    id uuid NOT NULL,
    post_id uuid NOT NULL,
    updated_by uuid,
    user_id uuid NOT NULL,
    user_nickname character varying(50) NOT NULL,
    content character varying(200) NOT NULL
);
CREATE TABLE public.likes (
    deleted_by_post_deletion boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_by uuid,
    id uuid NOT NULL,
    post_id uuid NOT NULL,
    updated_by uuid,
    user_id uuid NOT NULL,
    user_nickname character varying(100) NOT NULL
);
CREATE TABLE public.outbox_events (
    replay_count integer NOT NULL,
    retry_count integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    failed_at timestamp(6) with time zone,
    published_at timestamp(6) with time zone,
    aggregate_id uuid NOT NULL,
    id uuid NOT NULL,
    event_type character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    topic character varying(255) NOT NULL,
    payload jsonb NOT NULL,
    CONSTRAINT outbox_events_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying, 'DEAD'::character varying])::text[])))
);
CREATE TABLE public.post_media (
    sort_order integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    post_id uuid NOT NULL,
    media_type character varying(255) NOT NULL,
    s3key character varying(255) NOT NULL,
    CONSTRAINT post_media_media_type_check CHECK (((media_type)::text = ANY ((ARRAY['IMAGE'::character varying, 'VIDEO'::character varying])::text[])))
);
CREATE TABLE public.post_tags (
    created_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    post_id uuid NOT NULL,
    tag character varying(255) NOT NULL
);
CREATE TABLE public.posts (
    comment_count integer NOT NULL,
    latitude double precision NOT NULL,
    like_count integer NOT NULL,
    longitude double precision NOT NULL,
    show_location boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    deleted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_by uuid,
    id uuid NOT NULL,
    updated_by uuid,
    user_id uuid NOT NULL,
    author_nickname character varying(50),
    content character varying(500),
    neighborhood character varying(255),
    visibility character varying(255) NOT NULL,
    CONSTRAINT posts_visibility_check CHECK (((visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'FOLLOWERS_ONLY'::character varying, 'PRIVATE'::character varying])::text[])))
);
ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.likes
    ADD CONSTRAINT likes_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.post_media
    ADD CONSTRAINT post_media_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.post_tags
    ADD CONSTRAINT post_tags_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.posts
    ADD CONSTRAINT posts_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.likes
    ADD CONSTRAINT uq_likes_post_id_user_id UNIQUE (post_id, user_id);
ALTER TABLE ONLY public.post_media
    ADD CONSTRAINT uq_post_media_post_id_sort_order UNIQUE (post_id, sort_order);
ALTER TABLE ONLY public.post_tags
    ADD CONSTRAINT uq_post_tags_post_id_tag UNIQUE (post_id, tag);
CREATE INDEX idx_comments_post_id_created_at_id ON public.comments USING btree (post_id, created_at, id);
CREATE INDEX idx_likes_post_id_created_at ON public.likes USING btree (post_id, created_at DESC);
CREATE INDEX idx_outbox_events_status_created_at ON public.outbox_events USING btree (status, created_at);
CREATE INDEX idx_post_media_post_id_sort_order ON public.post_media USING btree (post_id, sort_order);
CREATE INDEX idx_post_tags_post_id ON public.post_tags USING btree (post_id);
CREATE INDEX idx_posts_created_at_id ON public.posts USING btree (created_at DESC, id DESC);
CREATE INDEX idx_posts_user_id_created_at ON public.posts USING btree (user_id, created_at DESC);
CREATE INDEX idx_posts_visibility_created_at_id ON public.posts USING btree (visibility, created_at DESC, id DESC);
ALTER TABLE ONLY public.post_media
    ADD CONSTRAINT fk1urcum9dtf0vgul7k405f4r2d FOREIGN KEY (post_id) REFERENCES public.posts(id);
ALTER TABLE ONLY public.post_tags
    ADD CONSTRAINT fkkifam22p4s1nm3bkmp1igcn5w FOREIGN KEY (post_id) REFERENCES public.posts(id);