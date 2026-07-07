"""
ProfileEmbeddingProducer 단위 테스트.
AIOKafkaProducer/Consumer를 fake/mock으로 대체하여 Kafka 없이 CI에서 실행 가능.

검증 항목:
  - PROFILE_EMBEDDING_UPDATED: 토픽/키(userId)/payload 필드/eventId(v7)/occurredAt(KST)
  - PROFILE_EMBEDDING_BULK_COMPLETED: 토픽/키 없음/payload 필드
  - 발행 실패 시 profile-embedding-dlq로 적재 (원본 topic/key/message/error 보존)
  - DLQ 적재까지 실패해도 예외를 전파하지 않음
  - reprocess_profile_embedding_dlq(): DLQ 메시지를 원래 topic/key로 재발행
"""
import json
import unittest
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4

from app.config.settings import settings
from app.embedding.infrastructure.kafka.profile_embedding_producer import (
    ProfileEmbeddingProducer,
    reprocess_profile_embedding_dlq,
)

_MODULE = "app.embedding.infrastructure.kafka.profile_embedding_producer"


class _FakeDlqConsumer:
    """AIOKafkaConsumer 대체"""

    def __init__(self, messages):
        self._batches = [{"tp": messages}, {}]
        self._i = 0
        self.commit = AsyncMock()

    async def start(self):
        pass

    async def stop(self):
        pass

    async def getmany(self, timeout_ms=None, max_records=None):
        batch = self._batches[self._i]
        self._i = min(self._i + 1, len(self._batches) - 1)
        return batch


def _dlq_msg(value: bytes) -> MagicMock:
    msg = MagicMock()
    msg.value = value
    return msg


class _ProducerTestBase(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.mock_producer = AsyncMock()
        ProfileEmbeddingProducer._producer = self.mock_producer

    def tearDown(self):
        ProfileEmbeddingProducer._producer = None


class TestPublishProfileUpdated(_ProducerTestBase):

    async def test_sends_to_configured_topic_with_userid_key(self):
        user_id = uuid4()
        await ProfileEmbeddingProducer.publish_profile_updated(user_id, [0.1, 0.2], 5, True)

        args, kwargs = self.mock_producer.send_and_wait.call_args
        self.assertEqual(args[0], settings.KAFKA_TOPIC_PROFILE_EMBEDDING_UPDATED)
        self.assertEqual(kwargs["key"], str(user_id).encode("utf-8"))

    async def test_payload_fields_match_spec(self):
        user_id = uuid4()
        await ProfileEmbeddingProducer.publish_profile_updated(user_id, [0.1, 0.2], 5, True)

        _, message = self.mock_producer.send_and_wait.call_args.args
        self.assertEqual(message["eventType"], "PROFILE_EMBEDDING_UPDATED")
        self.assertEqual(message["payload"]["userId"], str(user_id))
        self.assertEqual(message["payload"]["profileVector"], [0.1, 0.2])
        self.assertEqual(message["payload"]["recordCount"], 5)
        self.assertTrue(message["payload"]["active"])

    async def test_event_id_is_uuid_v7(self):
        await ProfileEmbeddingProducer.publish_profile_updated(uuid4(), [0.1], 1, False)

        _, message = self.mock_producer.send_and_wait.call_args.args
        # UUID v7 확인
        self.assertEqual(message["eventId"][14], "7")

    async def test_occurred_at_is_kst_offset(self):
        await ProfileEmbeddingProducer.publish_profile_updated(uuid4(), [0.1], 1, False)

        _, message = self.mock_producer.send_and_wait.call_args.args
        self.assertTrue(message["occurredAt"].endswith("+09:00"))

    async def test_noop_when_producer_not_started(self):
        ProfileEmbeddingProducer._producer = None
        await ProfileEmbeddingProducer.publish_profile_updated(uuid4(), [0.1], 1, False)  # 예외 없이 스킵


class TestPublishBulkCompleted(_ProducerTestBase):

    async def test_sends_without_key(self):
        await ProfileEmbeddingProducer.publish_bulk_completed("DAILY", 10, 2, 1)

        args, kwargs = self.mock_producer.send_and_wait.call_args
        self.assertEqual(args[0], settings.KAFKA_TOPIC_PROFILE_EMBEDDING_BULK_COMPLETED)
        self.assertNotIn("key", kwargs)

    async def test_payload_fields_match_spec(self):
        await ProfileEmbeddingProducer.publish_bulk_completed("MONTHLY", 10, 2, 1)

        _, message = self.mock_producer.send_and_wait.call_args.args
        self.assertEqual(message["eventType"], "PROFILE_EMBEDDING_BULK_COMPLETED")
        self.assertEqual(
            message["payload"],
            {"batchType": "MONTHLY", "totalUpdated": 10, "totalSkipped": 2, "totalFailed": 1},
        )


class TestPublishFailureGoesToDlq(_ProducerTestBase):

    async def test_delta_publish_failure_sends_to_dlq(self):
        """토픽 발행 실패 → profile-embedding-dlq에 원본 topic/key/message/error가 적재된다."""
        user_id = uuid4()
        self.mock_producer.send_and_wait.side_effect = [
            RuntimeError("kafka down"),  # 최초 발행 시도 실패
            None,                        # DLQ 적재는 성공
        ]

        await ProfileEmbeddingProducer.publish_profile_updated(user_id, [0.1], 3, False)

        self.assertEqual(self.mock_producer.send_and_wait.call_count, 2)
        dlq_topic, dlq_payload = self.mock_producer.send_and_wait.call_args_list[1].args
        self.assertEqual(dlq_topic, settings.KAFKA_TOPIC_PROFILE_EMBEDDING_DLQ)
        self.assertEqual(dlq_payload["topic"], settings.KAFKA_TOPIC_PROFILE_EMBEDDING_UPDATED)
        self.assertEqual(dlq_payload["key"], str(user_id))
        self.assertEqual(dlq_payload["message"]["payload"]["userId"], str(user_id))
        self.assertIn("kafka down", dlq_payload["error"])

    async def test_bulk_publish_failure_sends_to_dlq_with_no_key(self):
        self.mock_producer.send_and_wait.side_effect = [RuntimeError("kafka down"), None]

        await ProfileEmbeddingProducer.publish_bulk_completed("DAILY", 1, 0, 0)

        dlq_topic, dlq_payload = self.mock_producer.send_and_wait.call_args_list[1].args
        self.assertEqual(dlq_topic, settings.KAFKA_TOPIC_PROFILE_EMBEDDING_DLQ)
        self.assertIsNone(dlq_payload["key"])

    async def test_dlq_send_also_failing_does_not_raise(self):
        """DLQ 적재까지 실패해도 예외를 전파하지 않는다(배치가 멈추지 않도록)."""
        self.mock_producer.send_and_wait.side_effect = RuntimeError("완전히 다운")

        await ProfileEmbeddingProducer.publish_profile_updated(uuid4(), [0.1], 1, False)  # 예외 없이 종료


class TestReprocessProfileEmbeddingDlq(_ProducerTestBase):

    async def test_returns_zero_when_producer_not_started(self):
        ProfileEmbeddingProducer._producer = None
        count = await reprocess_profile_embedding_dlq()
        self.assertEqual(count, 0)

    async def test_republishes_message_to_original_topic_with_key(self):
        original_message = {"eventId": "x", "eventType": "PROFILE_EMBEDDING_UPDATED", "payload": {}}
        dlq_value = json.dumps({
            "topic": "profile-embedding-updated",
            "key": "user-123",
            "message": original_message,
            "error": "kafka down",
            "failed_at": "2026-01-01T00:00:00+00:00",
        }).encode("utf-8")
        fake_consumer = _FakeDlqConsumer([_dlq_msg(dlq_value)])

        with patch(f"{_MODULE}.AIOKafkaConsumer", return_value=fake_consumer):
            count = await reprocess_profile_embedding_dlq()

        self.assertEqual(count, 1)
        self.mock_producer.send_and_wait.assert_awaited_once_with(
            "profile-embedding-updated", original_message, key=b"user-123"
        )
        fake_consumer.commit.assert_awaited()

    async def test_republishes_bulk_message_without_key(self):
        original_message = {"eventId": "y", "eventType": "PROFILE_EMBEDDING_BULK_COMPLETED", "payload": {}}
        dlq_value = json.dumps({
            "topic": "profile-embedding-bulk-completed",
            "key": None,
            "message": original_message,
            "error": "kafka down",
            "failed_at": "2026-01-01T00:00:00+00:00",
        }).encode("utf-8")
        fake_consumer = _FakeDlqConsumer([_dlq_msg(dlq_value)])

        with patch(f"{_MODULE}.AIOKafkaConsumer", return_value=fake_consumer):
            count = await reprocess_profile_embedding_dlq()

        self.assertEqual(count, 1)
        self.mock_producer.send_and_wait.assert_awaited_once_with(
            "profile-embedding-bulk-completed", original_message, key=None
        )

    async def test_malformed_message_is_skipped_not_raised(self):
        fake_consumer = _FakeDlqConsumer([_dlq_msg(b"not valid json {{{")])

        with patch(f"{_MODULE}.AIOKafkaConsumer", return_value=fake_consumer):
            count = await reprocess_profile_embedding_dlq()

        self.assertEqual(count, 0)  # 스킵되고 예외 전파 없음


if __name__ == "__main__":
    unittest.main()
