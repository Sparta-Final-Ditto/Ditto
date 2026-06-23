import logging
from uuid import UUID

from app.config.settings import settings
from app.common.db.database import AsyncSessionLocal
from app.embedding.domain.algorithm.ema_calculator import update_profile
from app.embedding.infrastructure.repository.pg_post_embedding_repository import PgPostEmbeddingRepository
from app.embedding.infrastructure.repository.pg_user_profile_repository import PgUserProfileRepository

logger = logging.getLogger(__name__)


async def run_batch() -> None:
    """새벽 배치: FAILED 재처리 → 전체 유저 EMA 재계산."""
    logger.info("[Batch] 새벽 배치 시작")

    async with AsyncSessionLocal() as db:
        post_repo = PgPostEmbeddingRepository(db)
        profile_repo = PgUserProfileRepository(db)

        reset_count = await post_repo.reset_failed_to_done()
        if reset_count:
            logger.info(f"[Batch] FAILED → DONE 복구: {reset_count}건")

        user_ids: list[UUID] = await post_repo.find_all_user_ids_with_done_embeddings()
        logger.info(f"[Batch] EMA 재계산 대상 유저: {len(user_ids)}명")

        success, skip, fail = 0, 0, 0
        for user_id in user_ids:
            try:
                vectors = await post_repo.find_all_done_vectors_ordered(user_id)
                if not vectors:
                    skip += 1
                    continue

                ema = vectors[0]
                for vec in vectors[1:]:
                    ema = update_profile(ema, vec, settings.EMA_ALPHA)

                profile = await profile_repo.find_by_user_id(user_id)
                new_count = len(vectors)
                await profile_repo.upsert(
                    user_id=user_id,
                    vector=ema,
                    record_count=new_count,
                    active=new_count >= settings.MIN_RECORDS_FOR_MATCHING,
                    last_processed_record_id=profile.last_processed_record_id if profile else None,
                )
                success += 1
            except Exception as e:
                logger.error(f"[Batch] 유저 {user_id} EMA 재계산 실패: {e}")
                fail += 1

    logger.info(f"[Batch] 완료 — 성공: {success}, 스킵: {skip}, 실패: {fail}")
