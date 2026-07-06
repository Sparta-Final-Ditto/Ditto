import logging
from datetime import date
from uuid import UUID

from app.common.db.database import AsyncSessionLocal
from app.common.kafka.consumer_base import KafkaConsumerBase
from app.config.settings import settings
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.infrastructure.batch.batch_embedding import BatchEmbeddingRunner
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.infrastructure.repository.pg_post_embedding_repository import PgPostEmbeddingRepository
from app.embedding.infrastructure.repository.pg_user_profile_repository import PgUserProfileRepository

logger = logging.getLogger(__name__)


class UserRegisteredConsumer(KafkaConsumerBase):
    """
    user_service 'USER_REGISTERED' 토픽 수신.
    eventType: "USER_CREATED"              → vector=NULL stub 행 생성
    eventType: "USER_INTERESTS_REGISTERED" → 관심사 기반 초기 프로필 벡터 생성
    """

    def __init__(self) -> None:
        super().__init__(topic=settings.KAFKA_TOPIC_USER_EVENTS)

    async def handle(self, message: dict) -> None:
        event_type = message.get("eventType")
        if event_type == "USER_CREATED":
            await self._handle_user_created(message)
        elif event_type == "USER_INTERESTS_REGISTERED":
            await self._handle_user_interests_registered(message)

    async def _handle_user_created(self, message: dict) -> None:
        try:
            user_id = UUID(str(message["userId"]))
            gender: str = message["gender"]
            birthdate = date.fromisoformat(message["birthdate"])
        except (KeyError, ValueError) as e:
            logger.error(f"[UserRegisteredConsumer] USER_CREATED 파싱 실패: {e} | raw={message}")
            return

        async with AsyncSessionLocal() as db:
            svc = EmbeddingService(
                post_repo=PgPostEmbeddingRepository(db),
                profile_repo=PgUserProfileRepository(db),
                model=ModelLoader(),
                batch_runner=BatchEmbeddingRunner(),
            )
            try:
                await svc.init_user_profile(user_id, gender, birthdate)
                logger.info(f"[UserRegisteredConsumer] 프로필 stub 생성: user_id={user_id}")
            except Exception as e:
                logger.error(f"[UserRegisteredConsumer] 프로필 stub 생성 실패: user_id={user_id}, error={e}")

    async def _handle_user_interests_registered(self, message: dict) -> None:
        try:
            user_id = UUID(str(message["userId"]))
            hashtags: list[str] = message["hashtags"]
        except (KeyError, ValueError) as e:
            logger.error(f"[UserRegisteredConsumer] USER_INTERESTS_REGISTERED 파싱 실패: {e} | raw={message}")
            return

        async with AsyncSessionLocal() as db:
            svc = EmbeddingService(
                post_repo=PgPostEmbeddingRepository(db),
                profile_repo=PgUserProfileRepository(db),
                model=ModelLoader(),
                batch_runner=BatchEmbeddingRunner(),
            )
            try:
                await svc.register_user_interests(user_id, hashtags)
                logger.info(f"[UserRegisteredConsumer] 초기 프로필 벡터 생성: user_id={user_id}")
            except Exception as e:
                logger.error(f"[UserRegisteredConsumer] 초기 프로필 벡터 생성 실패: user_id={user_id}, error={e}")
