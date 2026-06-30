import json
import logging
from datetime import datetime, timezone

from aiokafka import AIOKafkaProducer

from app.config.settings import settings

logger = logging.getLogger(__name__)


class DlqProducer:
    _producer: AIOKafkaProducer | None = None

    @classmethod
    async def start(cls) -> None:
        cls._producer = AIOKafkaProducer(
            bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
        )
        await cls._producer.start()
        logger.info("[DlqProducer] 시작")

    @classmethod
    async def stop(cls) -> None:
        if cls._producer:
            await cls._producer.stop()
            cls._producer = None
            logger.info("[DlqProducer] 종료")

    @classmethod
    async def send(cls, original_message: dict, error: str) -> None:
        if cls._producer is None:
            logger.warning("[DlqProducer] 프로듀서 미초기화 — DLQ 전송 건너뜀")
            return
        payload = {
            "original_message": original_message,
            "error": error,
            "failed_at": datetime.now(timezone.utc).isoformat(),
        }
        try:
            await cls._producer.send_and_wait(settings.KAFKA_TOPIC_DLQ, payload)
            logger.info(f"[DlqProducer] DLQ 전송 완료: topic={settings.KAFKA_TOPIC_DLQ}")
        except Exception as e:
            logger.error(f"[DlqProducer] DLQ 전송 실패 (메시지 유실 가능): {e}")
