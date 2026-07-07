import asyncio
import json
import logging
from uuid import UUID

from aiokafka import AIOKafkaConsumer

from app.config.settings import settings
from app.common.db.database import AsyncSessionLocal
from app.embedding.application.port.batch_runner_port import BatchRunnerPort
from app.embedding.domain.algorithm.ema_calculator import update_profile
from app.embedding.domain.algorithm.post_text_builder import build_post_text
from app.embedding.infrastructure.kafka.profile_embedding_producer import (
    ProfileEmbeddingProducer,
    reprocess_profile_embedding_dlq,
)
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.infrastructure.repository.pg_post_embedding_repository import PgPostEmbeddingRepository
from app.embedding.infrastructure.repository.pg_user_profile_repository import PgUserProfileRepository

logger = logging.getLogger(__name__)


async def _reprocess_dlq() -> int:
    """DLQ 토픽에 쌓인 실패 메시지를 재임베딩하여 저장한다."""
    consumer = AIOKafkaConsumer(
        settings.KAFKA_TOPIC_DLQ,
        bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
        group_id=f"{settings.KAFKA_CONSUMER_GROUP}-dlq-reprocess",
        auto_offset_reset="earliest",
        enable_auto_commit=False,
    )
    await consumer.start()

    model = ModelLoader()
    count = 0

    try:
        while True:
            records = await consumer.getmany(timeout_ms=3000, max_records=100)
            if not records:
                break
            async with AsyncSessionLocal() as db:
                post_repo = PgPostEmbeddingRepository(db)
                for msgs in records.values():
                    for msg in msgs:
                        if msg.value is None:
                            continue
                        try:
                            dlq_msg = json.loads(msg.value.decode("utf-8"))
                            payload = dlq_msg["original_message"]["payload"]
                            post_id = UUID(payload["postId"])
                            user_id = UUID(payload["userId"])
                            content: str = payload["content"]
                            hashtags: list[str] = payload.get("tags", [])

                            text = build_post_text(content, hashtags)
                            vector = await asyncio.to_thread(model.encode, text)
                            await post_repo.save(post_id, user_id, vector)
                            count += 1
                            logger.info(f"[Batch] DLQ 재처리 완료: post_id={post_id}")
                        except Exception as e:
                            logger.error(f"[Batch] DLQ 재처리 실패: {e}")
            await consumer.commit()
    finally:
        await consumer.stop()

    return count


async def run_batch() -> None:
    """새벽 배치: DLQ 재처리 → FAILED 복구 → 전체 유저 EMA 재계산."""
    logger.info("[Batch] 새벽 배치 시작")

    dlq_count = await _reprocess_dlq()
    if dlq_count:
        logger.info(f"[Batch] DLQ 재처리: {dlq_count}건")

    profile_dlq_count = await reprocess_profile_embedding_dlq()
    if profile_dlq_count:
        logger.info(f"[Batch] profile-embedding DLQ 재처리: {profile_dlq_count}건")

    updated_profiles: list[tuple[UUID, list[float], int, bool]] = []

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
                    # record_count는 DB count로 두어 중복 집계 방지
                    new_count = await post_repo.count_done_by_user_id(user_id)
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
                    new_count = await post_repo.count_done_by_user_id(user_id)
                    new_last_id = all_records[-1][0]

                active_flag = new_count >= settings.MIN_RECORDS_FOR_MATCHING
                await profile_repo.upsert(
                    user_id=user_id,
                    vector=ema,
                    record_count=new_count,
                    active=active_flag,
                    last_processed_record_id=new_last_id,
                )
                updated_profiles.append((user_id, ema, new_count, active_flag))
                success += 1
            except Exception as e:
                logger.error(f"[Batch] 유저 {user_id} EMA 재계산 실패: {e}")
                fail += 1

    logger.info(f"[Batch] 완료 — 성공: {success}, 스킵: {skip}, 실패: {fail}")

    if len(updated_profiles) <= settings.PROFILE_SYNC_BULK_THRESHOLD:
        for user_id, vector, record_count, active in updated_profiles:
            await ProfileEmbeddingProducer.publish_profile_updated(user_id, vector, record_count, active)
        if updated_profiles:
            logger.info(f"[Batch] PROFILE_EMBEDDING_UPDATED {len(updated_profiles)}건 발행 완료")
    else:
        logger.info(f"[Batch] 변경 인원 {len(updated_profiles)}명 > 임계값({settings.PROFILE_SYNC_BULK_THRESHOLD}) — BULK_COMPLETED로 전환")
        await ProfileEmbeddingProducer.publish_bulk_completed(
            batch_type="DAILY",
            total_updated=success,
            total_skipped=skip,
            total_failed=fail,
        )


async def run_monthly_batch() -> None:
    """월배치: 전체 유저 대상 시간 감쇠 가중 평균으로 프로필 벡터 전체 재계산.

    weight = exp(-age_days / 7). DONE 게시글만 포함, DELETED 제외.
    게시글이 없는 유저는 기존 임베딩 유지.
    실행 후 last_processed_record_id를 최신 post_id로 갱신하여 일배치 증분이 이어지도록 한다.
    """
    from app.embedding.domain.algorithm.ema_calculator import time_decay_weighted_average

    logger.info("[MonthlyBatch] 월간 전체 재계산 시작")

    async with AsyncSessionLocal() as db:
        post_repo = PgPostEmbeddingRepository(db)
        profile_repo = PgUserProfileRepository(db)

        user_ids: list[UUID] = await post_repo.find_all_user_ids_with_done_embeddings()
        logger.info(f"[MonthlyBatch] 재계산 대상 유저: {len(user_ids)}명")

        success, skip, fail = 0, 0, 0
        for user_id in user_ids:
            try:
                records = await post_repo.find_all_done_for_monthly_batch(user_id)

                if not records:
                    skip += 1
                    continue

                vectors_with_time = [(vec, embedded_at) for _, vec, embedded_at in records]
                new_vector = time_decay_weighted_average(vectors_with_time)

                if new_vector is None:
                    skip += 1
                    continue

                last_post_id = records[-1][0]
                new_count = len(records)

                await profile_repo.upsert(
                    user_id=user_id,
                    vector=new_vector,
                    record_count=new_count,
                    active=new_count >= settings.MIN_RECORDS_FOR_MATCHING,
                    last_processed_record_id=last_post_id,
                )
                success += 1
            except Exception as e:
                logger.error(f"[MonthlyBatch] 유저 {user_id} 재계산 실패: {e}")
                fail += 1

    logger.info(f"[MonthlyBatch] 완료 — 성공: {success}, 스킵: {skip}, 실패: {fail}")

    await ProfileEmbeddingProducer.publish_bulk_completed(
        batch_type="MONTHLY",
        total_updated=success,
        total_skipped=skip,
        total_failed=fail,
    )


class BatchEmbeddingRunner(BatchRunnerPort):
    """Application의 BatchRunnerPort 구현체 — 기존 run_batch/run_monthly_batch를 위임."""

    async def run_daily(self) -> None:
        await run_batch()

    async def run_monthly(self) -> None:
        await run_monthly_batch()
