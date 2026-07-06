-- 동기화된 프로필 벡터 테이블 (CQRS Read Model)
-- embedding_service의 user_profile_embeddings를 복제하여
-- match_service에서 직접 HNSW 벡터 검색 수행

CREATE TABLE IF NOT EXISTS synced_profile_embeddings (
                                                         user_id    UUID PRIMARY KEY,
                                                         vector     vector(768) NOT NULL,
    gender     VARCHAR(6),
    birthdate  DATE,
    active     BOOLEAN NOT NULL DEFAULT false,
    synced_at  TIMESTAMPTZ NOT NULL DEFAULT now()
    );

-- HNSW 인덱스 (코사인 유사도 기반 ANN 검색)
-- m=16: 그래프 연결 수, ef_construction=64: 인덱스 빌드 정확도
CREATE INDEX idx_synced_embeddings_hnsw
    ON synced_profile_embeddings
    USING hnsw (vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- active 유저 필터링용 부분 인덱스
CREATE INDEX idx_synced_embeddings_active
    ON synced_profile_embeddings (active)
    WHERE active = true;

-- 성별 필터링용
CREATE INDEX idx_synced_embeddings_gender
    ON synced_profile_embeddings (gender);