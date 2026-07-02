import json
import logging
from datetime import datetime, timedelta, timezone
from uuid import UUID

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from uuid6 import uuid7

from app.config.settings import settings

logger = logging.getLogger(__name__)

KST = timezone(timedelta(hours=9))


class ProfileEmbeddingProducer:
    """match_service에 프로필 벡터 변경분을 알리는 이벤트 발행 (docs/embedding_kafka.md 참고)."""

    _producer: AIOKafkaProducer | None = None

    @classmethod
    async def start(cls) -> None:
        cls._producer = AIOKafkaProducer(
            bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
        )
        await cls._producer.start()
        logger.info("[ProfileEmbeddingProducer] 시작")

    @classmethod
    async def stop(cls) -> None:
        if cls._producer:
            await cls._producer.stop()
            cls._producer = None
            logger.info("[ProfileEmbeddingProducer] 종료")

    @classmethod
    def _envelope(cls, event_type: str, payload: dict) -> dict:
        return {
            "eventId": str(uuid7()),
            "eventType": event_type,
            "occurredAt": datetime.now(KST).isoformat(timespec="seconds"),
            "payload": payload,
        }

    @classmethod
    async def publish_profile_updated(
        cls,
        user_id: UUID,
        vector: list[float],
        record_count: int,
        active: bool,
    ) -> None:
        """유저별 델타 이벤트 — profile-embedding-updated (key=userId)."""
        if cls._producer is None:
            logger.warning("[ProfileEmbeddingProducer] 프로듀서 미초기화 — 발행 건너뜀")
            return
        message = cls._envelope(
            "PROFILE_EMBEDDING_UPDATED",
            {
                "userId": str(user_id),
                "profileVector": vector,
                "recordCount": record_count,
                "active": active,
            },
        )
        try:
            await cls._producer.send_and_wait(
                settings.KAFKA_TOPIC_PROFILE_EMBEDDING_UPDATED,
                message,
                key=str(user_id).encode("utf-8"),
            )
        except Exception as e:
            logger.error(f"[ProfileEmbeddingProducer] PROFILE_EMBEDDING_UPDATED 발행 실패: user_id={user_id}, error={e}")
            await cls._send_to_dlq(settings.KAFKA_TOPIC_PROFILE_EMBEDDING_UPDATED, str(user_id), message, str(e))

    @classmethod
    async def publish_bulk_completed(
        cls,
        batch_type: str,
        total_updated: int,
        total_skipped: int,
        total_failed: int,
    ) -> None:
        """배치 완료 신호 — profile-embedding-bulk-completed (키 없음, 배치당 1건)."""
        if cls._producer is None:
            logger.warning("[ProfileEmbeddingProducer] 프로듀서 미초기화 — 발행 건너뜀")
            return
        message = cls._envelope(
            "PROFILE_EMBEDDING_BULK_COMPLETED",
            {
                "batchType": batch_type,
                "totalUpdated": total_updated,
                "totalSkipped": total_skipped,
                "totalFailed": total_failed,
            },
        )
        try:
            await cls._producer.send_and_wait(settings.KAFKA_TOPIC_PROFILE_EMBEDDING_BULK_COMPLETED, message)
            logger.info(f"[ProfileEmbeddingProducer] PROFILE_EMBEDDING_BULK_COMPLETED 발행 완료: batch_type={batch_type}")
        except Exception as e:
            logger.error(f"[ProfileEmbeddingProducer] PROFILE_EMBEDDING_BULK_COMPLETED 발행 실패: {e}")
            await cls._send_to_dlq(settings.KAFKA_TOPIC_PROFILE_EMBEDDING_BULK_COMPLETED, None, message, str(e))

    @classmethod
    async def _send_to_dlq(cls, topic: str, key: str | None, message: dict, error: str) -> None:
        """발행 실패 메시지를 profile-embedding-dlq에 적재 — 다음 일배치에서 재처리."""
        if cls._producer is None:
            return
        dlq_payload = {
            "topic": topic,
            "key": key,
            "message": message,
            "error": error,
            "failed_at": datetime.now(timezone.utc).isoformat(),
        }
        try:
            await cls._producer.send_and_wait(settings.KAFKA_TOPIC_PROFILE_EMBEDDING_DLQ, dlq_payload)
            logger.info(f"[ProfileEmbeddingProducer] DLQ 적재 완료: original_topic={topic}")
        except Exception as e:
            logger.error(f"[ProfileEmbeddingProducer] DLQ 적재도 실패 (메시지 유실): {e}")


async def reprocess_profile_embedding_dlq() -> int:
    """profile-embedding-dlq에 쌓인 발행 실패 메시지를 원래 토픽으로 재발행한다."""
    if ProfileEmbeddingProducer._producer is None:
        logger.warning("[ProfileEmbeddingProducer] 프로듀서 미초기화 — DLQ 재처리 건너뜀")
        return 0

    consumer = AIOKafkaConsumer(
        settings.KAFKA_TOPIC_PROFILE_EMBEDDING_DLQ,
        bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
        group_id=f"{settings.KAFKA_CONSUMER_GROUP}-profile-embedding-dlq-reprocess",
        auto_offset_reset="earliest",
        enable_auto_commit=False,
    )
    await consumer.start()
    count = 0
    try:
        while True:
            records = await consumer.getmany(timeout_ms=3000, max_records=100)
            if not records:
                break
            for msgs in records.values():
                for msg in msgs:
                    if msg.value is None:
                        continue
                    try:
                        dlq_msg = json.loads(msg.value.decode("utf-8"))
                        topic = dlq_msg["topic"]
                        key = dlq_msg.get("key")
                        message = dlq_msg["message"]
                        await ProfileEmbeddingProducer._producer.send_and_wait(
                            topic, message, key=key.encode("utf-8") if key else None
                        )
                        count += 1
                    except Exception as e:
                        logger.error(f"[ProfileEmbeddingProducer] DLQ 재처리 실패: {e}")
            await consumer.commit()
    finally:
        await consumer.stop()

    return count
