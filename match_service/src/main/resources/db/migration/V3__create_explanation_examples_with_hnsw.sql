-- 매칭 설명 RAG용 예시 테이블
-- embedding_service의 /internal/embedding/embed-text 로 계산한 벡터를 저장
-- (시드 골든셋 + 실제 LLM 생성 이력이 쌓여 검색 풀이 됨)

CREATE TABLE IF NOT EXISTS explanation_examples (
    id           UUID PRIMARY KEY,
    content      TEXT NOT NULL,
    vector       vector(768) NOT NULL,
    common_tags  VARCHAR(200),
    score        INTEGER,
    source_type  VARCHAR(20) NOT NULL, -- SEED | GENERATED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HNSW 인덱스 (코사인 유사도 기반 ANN 검색)
CREATE INDEX idx_explanation_examples_hnsw
    ON explanation_examples
    USING hnsw (vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
