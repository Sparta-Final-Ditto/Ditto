"""
check_flows.py 기반 — PostConsumer.handle() 단위 테스트.
Kafka·DB·모델 없이 CI에서 실행 가능.
"""
import json
import uuid
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from app.embedding.application.event.post_consumer import PostConsumer

_CONSUMER_MODULE = "app.embedding.application.event.post_consumer"


def _make_message(
    event_type: str = "POST_CREATED",
    post_id: str | None = None,
    user_id: str | None = None,
    content: str = "게시글 내용",
    tags: list | None = None,
) -> dict:
    payload = {
        "postId": post_id or str(uuid.uuid4()),
        "userId": user_id or str(uuid.uuid4()),
        "content": content,
        "tags": tags if tags is not None else ["태그1"],
    }
    return {"eventType": event_type, "payload": json.dumps(payload)}


class TestPostConsumerHandle(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_svc = AsyncMock()
        self.mock_post_repo = AsyncMock()

        mock_db = MagicMock()
        mock_db.__aenter__ = AsyncMock(return_value=MagicMock())
        mock_db.__aexit__ = AsyncMock(return_value=False)

        p_session = patch(f"{_CONSUMER_MODULE}.AsyncSessionLocal", return_value=mock_db)
        p_post = patch(f"{_CONSUMER_MODULE}.PgPostEmbeddingRepository", return_value=self.mock_post_repo)
        p_profile = patch(f"{_CONSUMER_MODULE}.PgUserProfileRepository", return_value=AsyncMock())
        p_model = patch(f"{_CONSUMER_MODULE}.ModelLoader", return_value=MagicMock())
        p_svc = patch(f"{_CONSUMER_MODULE}.EmbeddingService", return_value=self.mock_svc)

        for p in [p_session, p_post, p_profile, p_model, p_svc]:
            p.start()
            self.addCleanup(p.stop)

        self.consumer = PostConsumer()

    async def test_post_created_calls_embed_and_store(self):
        """POST_CREATED 이벤트 — embed_and_store() 호출."""
        msg = _make_message(content="한강 자전거", tags=["한강"])
        await self.consumer.handle(msg)
        self.mock_svc.embed_and_store.assert_called_once()

    async def test_non_post_created_event_ignored(self):
        """POST_CREATED 외 이벤트 — embed_and_store 미호출."""
        for event_type in ["POST_UPDATED", "POST_DELETED", "USER_CREATED"]:
            self.mock_svc.embed_and_store.reset_mock()
            await self.consumer.handle({"eventType": event_type, "payload": "{}"})
            self.mock_svc.embed_and_store.assert_not_called()

    async def test_payload_parsed_correctly(self):
        """postId·userId·content·tags가 올바르게 파싱되어 embed_and_store에 전달된다."""
        post_id = str(uuid.uuid4())
        user_id = str(uuid.uuid4())
        msg = _make_message(post_id=post_id, user_id=user_id, content="내용", tags=["태그A"])

        await self.consumer.handle(msg)

        call_args = self.mock_svc.embed_and_store.call_args
        self.assertEqual(str(call_args.args[0]), post_id)
        self.assertEqual(str(call_args.args[1]), user_id)
        self.assertEqual(call_args.args[2], "내용")
        self.assertIn("태그A", call_args.args[3])

    async def test_missing_tags_defaults_to_empty_list(self):
        """tags 키 없는 페이로드 — hashtags=[] 기본값 처리."""
        payload = {"postId": str(uuid.uuid4()), "userId": str(uuid.uuid4()), "content": "내용"}
        msg = {"eventType": "POST_CREATED", "payload": json.dumps(payload)}

        await self.consumer.handle(msg)

        call_args = self.mock_svc.embed_and_store.call_args
        self.assertEqual(call_args.args[3], [])

    async def test_invalid_payload_json_logs_and_returns(self):
        """페이로드 JSON 파싱 실패 — 예외 전파 없음."""
        msg = {"eventType": "POST_CREATED", "payload": "not-json"}
        await self.consumer.handle(msg)  # 예외 없이 반환
        self.mock_svc.embed_and_store.assert_not_called()

    async def test_missing_required_field_logs_and_returns(self):
        """필수 필드(postId) 누락 — 예외 전파 없음."""
        payload = {"userId": str(uuid.uuid4()), "content": "내용"}
        msg = {"eventType": "POST_CREATED", "payload": json.dumps(payload)}
        await self.consumer.handle(msg)
        self.mock_svc.embed_and_store.assert_not_called()

    async def test_invalid_uuid_format_logs_and_returns(self):
        """UUID 형식 오류 — 예외 전파 없음."""
        payload = {"postId": "not-a-uuid", "userId": str(uuid.uuid4()), "content": "내용"}
        msg = {"eventType": "POST_CREATED", "payload": json.dumps(payload)}
        await self.consumer.handle(msg)
        self.mock_svc.embed_and_store.assert_not_called()

    async def test_retry_success_on_second_attempt(self):
        """1회 실패 → 2회 성공 시 FAILED 마킹 없음."""
        self.mock_svc.embed_and_store.side_effect = [Exception("1회 실패"), None]
        msg = _make_message()

        with patch("asyncio.sleep", new_callable=AsyncMock):
            await self.consumer.handle(msg)

        self.mock_post_repo.update_status.assert_not_called()

    async def test_three_failures_marks_failed(self):
        """3회 모두 실패 — update_status('FAILED') 호출."""
        self.mock_svc.embed_and_store.side_effect = Exception("계속 실패")
        msg = _make_message()

        with patch("asyncio.sleep", new_callable=AsyncMock):
            await self.consumer.handle(msg)

        self.mock_post_repo.update_status.assert_called_once()
        args = self.mock_post_repo.update_status.call_args.args
        self.assertEqual(args[1], "FAILED")


if __name__ == "__main__":
    unittest.main()
