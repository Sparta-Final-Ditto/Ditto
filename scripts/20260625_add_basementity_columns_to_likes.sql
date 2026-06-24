-- =============================================================================
-- Migration: likes 테이블에 BaseEntity 컬럼 추가
-- Date: 2026-06-25
-- Background: Like 엔티티가 BaseEntity를 상속하게 되어 likes 테이블에
--             created_by, updated_at, updated_by, deleted_at, deleted_by
--             컬럼이 필요해졌다. ddl-auto=validate 환경이므로 수동 적용 필요.
--
-- 적용 방법:
--   1. 스테이징/운영 DB에 접속 후 아래 SQL을 순서대로 실행한다.
--      $ psql -h <host> -U <user> -d <dbname> -f scripts/20260625_add_basementity_columns_to_likes.sql
--   2. 서버 재기동 전에 반드시 이 SQL이 먼저 적용되어야 한다.
--      (ddl-auto=validate가 컬럼 존재를 검증하므로, 누락 시 애플리케이션 기동 실패)
-- =============================================================================

BEGIN;

-- 1. 신규 컬럼 추가
ALTER TABLE likes
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_by UUID,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS deleted_by UUID;

-- 2. created_at 타입을 TIMESTAMP WITH TIME ZONE으로 통일
--    (기존 컬럼이 timezone 없는 TIMESTAMP인 경우에만 필요; 이미 timestamptz이면 무해)
ALTER TABLE likes
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';

-- 3. updated_at 백필: 기존 행은 created_at 값으로 채운다
UPDATE likes
SET updated_at = created_at
WHERE updated_at IS NULL;

-- 4. updated_at NOT NULL 제약 적용 (백필 완료 후)
ALTER TABLE likes
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now();

COMMIT;
