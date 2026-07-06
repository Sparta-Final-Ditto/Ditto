import asyncio
import logging
from uuid import UUID

from app.common.db.database import AsyncSessionLocal
from app.common.kafka.consumer_base import KafkaConsumerBase
from app.common.kafka.dlq_producer import DlqProducer
from app.config.settings import settings
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.infrastructure.batch.batch_embedding import BatchEmbeddingRunner
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.infrastructure.repository.pg_post_embedding_repository import PgPostEmbeddingRepository
from app.embedding.infrastructure.repository.pg_user_profile_repository import PgUserProfileRepository

logger = logging.getLogger(__name__)


class PostConsumer(KafkaConsumerBase):
    """
    feed_service 'post-events' 토픽 수신.
    Envelope 구조: { "eventId", "eventType", "occurredAt", "payload": { ... } }
    POST_CREATED      payload: { "postId", "userId", "content", "tags", ... }
    POST_DELETED      payload: { "postId", "ownerId", "deletedBy", "deleteType", "deletedAt" }
    POST_HARD_DELETED payload: { "postId", "authorId", "deletedBy" }
    POST_RESTORED     payload: { "postId", "authorId", "restoredBy" }
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
                batch_runner=BatchEmbeddingRunner(),
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
            svc = EmbeddingService(
                post_repo=PgPostEmbeddingRepository(db),
                profile_repo=PgUserProfileRepository(db),
                model=ModelLoader(),
                batch_runner=BatchEmbeddingRunner(),
            )
            await svc.handle_post_deleted(post_id, user_id)

    async def _handle_post_hard_deleted(self, message: dict) -> None:
        try:
            payload: dict = message["payload"]
            post_id = UUID(payload["postId"])
            author_id = UUID(payload["authorId"])
        except (KeyError, ValueError, TypeError) as e:
            logger.error(f"[PostConsumer] POST_HARD_DELETED 파싱 실패: {e} | raw={message}")
            return

        async with AsyncSessionLocal() as db:
            svc = EmbeddingService(
                post_repo=PgPostEmbeddingRepository(db),
                profile_repo=PgUserProfileRepository(db),
                model=ModelLoader(),
                batch_runner=BatchEmbeddingRunner(),
            )
            await svc.handle_post_hard_deleted(post_id, author_id)

    async def _handle_post_restored(self, message: dict) -> None:
        try:
            payload: dict = message["payload"]
            post_id = UUID(payload["postId"])
            author_id = UUID(payload["authorId"])
        except (KeyError, ValueError, TypeError) as e:
            logger.error(f"[PostConsumer] POST_RESTORED 파싱 실패: {e} | raw={message}")
            return

        async with AsyncSessionLocal() as db:
            svc = EmbeddingService(
                post_repo=PgPostEmbeddingRepository(db),
                profile_repo=PgUserProfileRepository(db),
                model=ModelLoader(),
                batch_runner=BatchEmbeddingRunner(),
            )
            await svc.handle_post_restored(post_id, author_id)
