"""
KafkaConsumerBase 수동 offset commit 단위 테스트.
Kafka 없이 CI에서 실행 가능.

검증 항목:
  - handle() 성공 시 commit() 호출
  - handle() 예외 시 commit() 미호출 (메시지 재전달 보장)
  - msg.value=None 메시지 건너뜀
  - 메시지 여러 건: 성공한 건에 대해서만 commit()
"""
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from app.common.kafka.consumer_base import KafkaConsumerBase

_BASE_MODULE = "app.common.kafka.consumer_base"


class _ConcreteConsumer(KafkaConsumerBase):
    handle: AsyncMock

    def __init__(self):
        super().__init__(topic="test-topic")
        self.handle = AsyncMock()


def _make_msg(value=None):
    msg = MagicMock()
    msg.value = value if value is not None else {"eventType": "TEST"}
    return msg


class _FakeKafkaConsumer:
    """AIOKafkaConsumer 대체 — 주입된 메시지를 순서대로 yield."""

    def __init__(self, messages):
        self._messages = iter(messages)
        self.commit = AsyncMock()

    async def start(self): pass
    async def stop(self): pass

    def __aiter__(self):
        return self

    async def __anext__(self):
        try:
            return next(self._messages)
        except StopIteration:
            raise StopAsyncIteration


class TestManualCommit(unittest.IsolatedAsyncioTestCase):

    async def _run(self, consumer, messages):
        fake = _FakeKafkaConsumer(messages)
        with patch(f"{_BASE_MODULE}.AIOKafkaConsumer", return_value=fake):
            await consumer.start()
        return fake

    async def test_commit_called_after_successful_handle(self):
        """handle() 성공 → commit() 1회 호출."""
        consumer = _ConcreteConsumer()
        fake = await self._run(consumer, [_make_msg()])
        fake.commit.assert_called_once()

    async def test_commit_not_called_when_handle_raises(self):
        """handle() 예외 → commit() 미호출."""
        consumer = _ConcreteConsumer()
        consumer.handle.side_effect = Exception("처리 실패")
        fake = await self._run(consumer, [_make_msg()])
        fake.commit.assert_not_called()

    async def test_commit_called_per_successful_message(self):
        """메시지 3건 모두 성공 → commit() 3회."""
        consumer = _ConcreteConsumer()
        fake = await self._run(consumer, [_make_msg() for _ in range(3)])
        self.assertEqual(fake.commit.call_count, 3)

    async def test_none_value_message_skips_handle_and_commit(self):
        """msg.value=None → handle()·commit() 미호출."""
        consumer = _ConcreteConsumer()
        msg = MagicMock()
        msg.value = None
        fake = await self._run(consumer, [msg])
        consumer.handle.assert_not_called()
        fake.commit.assert_not_called()

    async def test_failed_message_does_not_block_subsequent(self):
        """1번 실패, 2번 성공 → commit() 1회 (2번 메시지에 대해서만)."""
        consumer = _ConcreteConsumer()
        consumer.handle.side_effect = [Exception("실패"), None]
        fake = await self._run(consumer, [_make_msg(), _make_msg()])
        self.assertEqual(fake.commit.call_count, 1)


if __name__ == "__main__":
    unittest.main()
