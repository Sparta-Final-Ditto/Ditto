import json
import logging
from aiokafka import AIOKafkaConsumer
from app.config.settings import settings

logger = logging.getLogger(__name__)


class KafkaConsumerBase:

    def __init__(self, topic: str):
        self._topic = topic
        self._consumer: AIOKafkaConsumer | None = None

    async def start(self) -> None:
        self._consumer = AIOKafkaConsumer(
            self._topic,
            bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
            group_id=settings.KAFKA_CONSUMER_GROUP,
            value_deserializer=lambda v: json.loads(v.decode("utf-8")),
            auto_offset_reset="earliest",
            enable_auto_commit=False,
        )
        await self._consumer.start()
        try:
            async for msg in self._consumer:
                if msg.value is None:
                    continue
                try:
                    # 파티션 내 순차 처리 — EMA 계산 순서 보장을 위해 의도적으로 순차 처리.
                    # 스케일 아웃: feed_service에서 user_id를 partition key로 설정 후
                    #              embedding_service 인스턴스를 파티션 수만큼 띄우면
                    #              인스턴스 간 병렬 처리 + 유저별 순서 보장 동시 달성.
                    await self.handle(msg.value)
                    await self._consumer.commit()
                except Exception as e:
                    logger.error(f"[{self.__class__.__name__}] 메시지 처리 실패: {e}")
        finally:
            await self._consumer.stop()

    async def handle(self, message: dict) -> None:
        raise NotImplementedError
