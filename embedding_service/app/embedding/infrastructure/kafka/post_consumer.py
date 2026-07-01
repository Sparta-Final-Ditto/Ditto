import asyncio
import logging
from datetime import datetime, timedelta, timezone
from uuid import UUID

from app.common.db.database import AsyncSessionLocal
from app.common.kafka.consumer_base import KafkaConsumerBase
from app.common.kafka.dlq_producer import DlqProducer
from app.config.settings import settings
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.infrastructure.repository.pg_post_embedding_repository import PgPostEmbeddingRepository
from app.embedding.infrastructure.repository.pg_user_profile_repository import PgUserProfileRepository

logger = logging.getLogger(__name__)


class PostConsumer(KafkaConsumerBase):
    """
    feed_service 'post-events' 토픽 수신.
    Envelope 구조: { "eventId", "eventType", "occurredAt", "payload": { ... } }
    POST_CREATED payload: { "postId", "userId", "content", "tags", ... }
    POST_DELETED payload: { "postId", "ownerId", "deletedBy", "deleteType", "deletedAt" }
    """

    def __init__(self) -> None:
        super().__init__(topic=settings.KAFKA_TOPIC_POST_EVENTS)

    async def handle(self, message: dict) -> None:
        event_type = message.get("eventType")
        if event_type == "POST_CREATED":
            await self._handle_post_created(message)
        elif event_type == "POST_DELETED":
            await self._handle_post_deleted(message)
        elif event_type == "POST_HARD_DELETED":
            await self._handle_post_hard_deleted(message)
        elif event_type == "POST_RESTORED":
            await self._handle_post_restored(message)

    async def _handle_post_created(self, message: dict) -> None:
        try:
            payload: dict = message["payload"]
            post_id = UUID(payload["postId"])
            user_id = UUID(payload["userId"])
            content: str = payload["content"]
            hashtags: list[str] = payload.get("tags", [])
        except (KeyError, ValueError, TypeError) as e:
            logger.error(f"[PostConsumer] 페이로드 파싱 실패: {e} | raw={message}")
            return

        async with AsyncSessionLocal() as db:
            post_repo = PgPostEmbeddingRepository(db)
            svc = EmbeddingService(
                post_repo=post_repo,
                profile_repo=PgUserProfileRepository(db),
                model=ModelLoader(),
            )
            for attempt in range(1, 4):
                try:
                    await svc.embed_and_store(post_id, user_id, content, hashtags)
                    logger.info(f"[PostConsumer] 임베딩 완료: post_id={post_id}, user_id={user_id}")
                    break
                except Exception as e:
                    logger.warning(f"[PostConsumer] 임베딩 실패 (시도 {attempt}/3): post_id={post_id}, error={e}")
                    if attempt == 3:
                        logger.error(f"[PostConsumer] 최대 재시도 초과 → FAILED: post_id={post_id}")
                        try:
                            await post_repo.update_status(post_id, "FAILED")
                        except Exception as db_err:
                            logger.warning(f"[PostConsumer] FAILED 상태 업데이트 실패: {db_err}")
                        await DlqProducer.send(message, str(e))
                    else:
                        await asyncio.sleep(5)

    async def _handle_post_deleted(self, message: dict) -> None:
        try:
            payload: dict = message["payload"]
            post_id = UUID(payload["postId"])
            user_id = UUID(payload["ownerId"])
        except (KeyError, ValueError, TypeError) as e:
            logger.error(f"[PostConsumer] POST_DELETED 파싱 실패: {e} | raw={message}")
            return

        async with AsyncSessionLocal() as db:
            post_repo = PgPostEmbeddingRepository(db)
            embedding = await post_repo.find_by_post_id(post_id)

            if embedding is None or embedding.embedded_at is None:
                return

            KST = timezone(timedelta(hours=9))
            today_kst = datetime.now(KST).date()
            embedded_date_kst = embedding.embedded_at.astimezone(KST).date()
            # KST 캘린더 날짜가 다르면 이미 다음 배치 처리 대상이 아님 — PASS
            if embedded_date_kst != today_kst:
                logger.info(f"[PostConsumer] 당일 게시글 아님 — PASS: post_id={post_id}")
                return

            await post_repo.update_status(post_id, "DELETED")
            logger.info(f"[PostConsumer] 당일 게시글 DELETED 처리: post_id={post_id}")

            done_count = await post_repo.count_done_by_user_id(user_id)
            profile_repo = PgUserProfileRepository(db)
            await profile_repo.sync_count_and_active(user_id, done_count)

    async def _handle_post_hard_deleted(self, message: dict) -> None:
        try:
            payload: dict = message["payload"]
            post_id = UUID(payload["postId"])
            author_id = UUID(payload["authorId"])
        except (KeyError, ValueError, TypeError) as e:
            logger.error(f"[PostConsumer] POST_HARD_DELETED 파싱 실패: {e} | raw={message}")
            return

        async with AsyncSessionLocal() as db:
            post_repo = PgPostEmbeddingRepository(db)
            embedding = await post_repo.find_by_post_id(post_id)

            if embedding is None:
                logger.info(f"[PostConsumer] 임베딩 없음 — PASS: post_id={post_id}")
                return

            await post_repo.delete_by_post_id(post_id)
            logger.info(f"[PostConsumer] hard delete 완료: post_id={post_id}")

            # 엣지케이스: soft delete 이벤트 누락으로 DONE 상태인 경우 record_count 보정
            if embedding.embedding_status == "DONE":
                done_count = await post_repo.count_done_by_user_id(author_id)
                profile_repo = PgUserProfileRepository(db)
                await profile_repo.sync_count_and_active(author_id, done_count)

    async def _handle_post_restored(self, message: dict) -> None:
        try:
            payload: dict = message["payload"]
            post_id = UUID(payload["postId"])
            author_id = UUID(payload["authorId"])
        except (KeyError, ValueError, TypeError) as e:
            logger.error(f"[PostConsumer] POST_RESTORED 파싱 실패: {e} | raw={message}")
            return

        async with AsyncSessionLocal() as db:
            post_repo = PgPostEmbeddingRepository(db)
            embedding = await post_repo.find_by_post_id(post_id)

            if embedding is None:
                logger.info(f"[PostConsumer] 임베딩 없음 — PASS: post_id={post_id}")
                return

            if embedding.embedding_status != "DELETED":
                logger.info(f"[PostConsumer] DELETED 상태 아님 — PASS: post_id={post_id}, status={embedding.embedding_status}")
                return

            await post_repo.update_status(post_id, "DONE")
            logger.info(f"[PostConsumer] 게시글 복구 DONE 처리: post_id={post_id}")

            # record_count/active 즉시 갱신, 벡터 재계산은 월배치에서 처리
            done_count = await post_repo.count_done_by_user_id(author_id)
            profile_repo = PgUserProfileRepository(db)
            await profile_repo.sync_count_and_active(author_id, done_count)
