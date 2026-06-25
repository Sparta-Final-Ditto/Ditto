import asyncio
import logging
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
    payload: { "postId", "userId", "content", "tags", ... }
    """

    def __init__(self) -> None:
        super().__init__(topic=settings.KAFKA_TOPIC_POST_EVENTS)

    async def handle(self, message: dict) -> None:
        if message.get("eventType") != "POST_CREATED":
            return

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
