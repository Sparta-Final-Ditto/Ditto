-- SyncedProfileEmbedding 엔티티에 neighborhood 필드가 추가되었으나
-- 테이블 생성 마이그레이션(V2)에 반영되지 않아 스키마 드리프트 발생 — 컬럼 추가로 정합화
ALTER TABLE synced_profile_embeddings
    ADD COLUMN neighborhood VARCHAR(100);
