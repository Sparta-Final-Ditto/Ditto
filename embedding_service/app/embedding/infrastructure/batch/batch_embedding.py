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
                profile = await profile_repo.find_by_user_id(user_id)
                last_id = profile.last_processed_record_id if profile else None

                if last_id is not None and profile is not None and profile.vector is not None:
                    # 증분: 마지막 처리 이후 신규 벡터만 EMA 갱신
                    new_records = await post_repo.find_done_vectors_after(user_id, last_id)
                    if not new_records:
                        skip += 1
                        continue
                    ema = list(profile.vector)
                    for _, vec in new_records:
                        ema = update_profile(ema, vec, settings.EMA_ALPHA)
                    new_count = (profile.record_count or 0) + len(new_records)
                    new_last_id = new_records[-1][0]
                else:
                    # 전체 재계산: 첫 배치 실행 또는 프로필 벡터 없음
                    all_records = await post_repo.find_done_vectors_after(user_id, None)
                    if not all_records:
                        skip += 1
                        continue
                    ema = list(all_records[0][1])
                    for _, vec in all_records[1:]:
                        ema = update_profile(ema, vec, settings.EMA_ALPHA)
                    new_count = len(all_records)
                    new_last_id = all_records[-1][0]

                await profile_repo.upsert(
                    user_id=user_id,
                    vector=ema,
                    record_count=new_count,
                    active=new_count >= settings.MIN_RECORDS_FOR_MATCHING,
                    last_processed_record_id=new_last_id,
                )
                success += 1
            except Exception as e:
                logger.error(f"[Batch] 유저 {user_id} EMA 재계산 실패: {e}")
                fail += 1

    logger.info(f"[Batch] 완료 — 성공: {success}, 스킵: {skip}, 실패: {fail}")
