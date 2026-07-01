"""
PostConsumer.handle() 단위 테스트.
Kafka·DB·모델 없이 CI에서 실행 가능.

feed_service Envelope 구조:
  { "eventId": str, "eventType": str, "occurredAt": str, "payload": dict }
payload는 consumer_base의 json.loads()를 거쳐 이미 dict로 전달된다.
"""
import uuid
import unittest
from datetime import datetime, timezone, timedelta
from unittest.mock import AsyncMock, MagicMock, patch

from app.embedding.domain.model.post_embedding import PostEmbedding
from app.embedding.infrastructure.kafka.post_consumer import PostConsumer

_CONSUMER_MODULE = "app.embedding.infrastructure.kafka.post_consumer"


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
    return {
        "eventId": str(uuid.uuid4()),
        "eventType": event_type,
        "occurredAt": "2026-06-25T00:00:00Z",
        "payload": payload,
    }


def _make_deleted_message(
    post_id: str | None = None,
    owner_id: str | None = None,
) -> dict:
    return {
        "eventId": str(uuid.uuid4()),
        "eventType": "POST_DELETED",
        "occurredAt": "2026-06-25T00:00:00Z",
        "payload": {
            "postId": post_id or str(uuid.uuid4()),
            "ownerId": owner_id or str(uuid.uuid4()),
            "deletedBy": str(uuid.uuid4()),
            "deleteType": "SOFT",
            "deletedAt": "2026-06-25T00:00:00Z",
        },
    }


def _make_restored_message(
    post_id: str | None = None,
    author_id: str | None = None,
) -> dict:
    return {
        "eventId": str(uuid.uuid4()),
        "eventType": "POST_RESTORED",
        "occurredAt": "2026-06-25T00:00:00Z",
        "payload": {
            "postId": post_id or str(uuid.uuid4()),
            "authorId": author_id or str(uuid.uuid4()),
            "restoredBy": str(uuid.uuid4()),
        },
    }


def _make_hard_deleted_message(
    post_id: str | None = None,
    author_id: str | None = None,
) -> dict:
    return {
        "eventId": str(uuid.uuid4()),
        "eventType": "POST_HARD_DELETED",
        "occurredAt": "2026-06-25T00:00:00Z",
        "payload": {
            "postId": post_id or str(uuid.uuid4()),
            "authorId": author_id or str(uuid.uuid4()),
            "deletedBy": str(uuid.uuid4()),
        },
    }


def _make_post_embedding(
    embedded_at: datetime,
    embedding_status: str = "DONE",
) -> PostEmbedding:
    return PostEmbedding(
        post_id=uuid.uuid4(),
        user_id=uuid.uuid4(),
        vector=[0.0] * 768,
        embedding_status=embedding_status,
        embedded_at=embedded_at,
    )


class TestPostConsumerHandle(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_svc = AsyncMock()
        self.mock_post_repo = AsyncMock()
        self.mock_profile_repo = AsyncMock()

        mock_db = MagicMock()
        mock_db.__aenter__ = AsyncMock(return_value=MagicMock())
        mock_db.__aexit__ = AsyncMock(return_value=False)

        p_session = patch(f"{_CONSUMER_MODULE}.AsyncSessionLocal", return_value=mock_db)
        p_post = patch(f"{_CONSUMER_MODULE}.PgPostEmbeddingRepository", return_value=self.mock_post_repo)
        p_profile = patch(f"{_CONSUMER_MODULE}.PgUserProfileRepository", return_value=self.mock_profile_repo)
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
        """POST_CREATED·POST_DELETED·POST_HARD_DELETED 외 이벤트 — embed_and_store 미호출."""
        for event_type in ["POST_UPDATED", "USER_CREATED"]:
            self.mock_svc.embed_and_store.reset_mock()
            await self.consumer.handle({"eventType": event_type, "payload": {}})
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
        msg = {"eventType": "POST_CREATED", "payload": payload}

        await self.consumer.handle(msg)

        call_args = self.mock_svc.embed_and_store.call_args
        self.assertEqual(call_args.args[3], [])

    async def test_invalid_payload_type_logs_and_returns(self):
        """payload가 dict가 아닌 타입(str 등) — 예외 전파 없음."""
        msg = {"eventType": "POST_CREATED", "payload": "not-a-dict"}
        await self.consumer.handle(msg)
        self.mock_svc.embed_and_store.assert_not_called()

    async def test_missing_payload_key_logs_and_returns(self):
        """payload 키 자체가 없는 메시지 — 예외 전파 없음."""
        msg = {"eventType": "POST_CREATED"}
        await self.consumer.handle(msg)
        self.mock_svc.embed_and_store.assert_not_called()

    async def test_missing_required_field_logs_and_returns(self):
        """필수 필드(postId) 누락 — 예외 전파 없음."""
        payload = {"userId": str(uuid.uuid4()), "content": "내용"}
        msg = {"eventType": "POST_CREATED", "payload": payload}
        await self.consumer.handle(msg)
        self.mock_svc.embed_and_store.assert_not_called()

    async def test_invalid_uuid_format_logs_and_returns(self):
        """UUID 형식 오류 — 예외 전파 없음."""
        payload = {"postId": "not-a-uuid", "userId": str(uuid.uuid4()), "content": "내용"}
        msg = {"eventType": "POST_CREATED", "payload": payload}
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


class TestPostConsumerPostDeleted(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_post_repo = AsyncMock()
        self.mock_profile_repo = AsyncMock()

        mock_db = MagicMock()
        mock_db.__aenter__ = AsyncMock(return_value=MagicMock())
        mock_db.__aexit__ = AsyncMock(return_value=False)

        p_session = patch(f"{_CONSUMER_MODULE}.AsyncSessionLocal", return_value=mock_db)
        p_post = patch(f"{_CONSUMER_MODULE}.PgPostEmbeddingRepository", return_value=self.mock_post_repo)
        p_profile = patch(f"{_CONSUMER_MODULE}.PgUserProfileRepository", return_value=self.mock_profile_repo)
        p_model = patch(f"{_CONSUMER_MODULE}.ModelLoader", return_value=MagicMock())
        p_svc = patch(f"{_CONSUMER_MODULE}.EmbeddingService", return_value=AsyncMock())

        for p in [p_session, p_post, p_profile, p_model, p_svc]:
            p.start()
            self.addCleanup(p.stop)

        self.consumer = PostConsumer()

    async def test_today_post_marked_deleted(self):
        """KST 당일 게시글 삭제 — status=DELETED 및 프로필 갱신."""
        KST = timezone(timedelta(hours=9))
        today_kst = datetime.now(KST)
        self.mock_post_repo.find_by_post_id.return_value = _make_post_embedding(embedded_at=today_kst)
        self.mock_post_repo.count_done_by_user_id.return_value = 2

        msg = _make_deleted_message()
        await self.consumer.handle(msg)

        self.mock_post_repo.update_status.assert_called_once()
        args = self.mock_post_repo.update_status.call_args.args
        self.assertEqual(args[1], "DELETED")

        self.mock_profile_repo.sync_count_and_active.assert_called_once_with(unittest.mock.ANY, 2)

    async def test_old_post_ignored(self):
        """KST 날짜가 다른 게시글 삭제 — update_status 미호출 (어제 게시글 오늘 삭제 포함)."""
        KST = timezone(timedelta(hours=9))
        yesterday_kst = datetime.now(KST) - timedelta(days=1)
        self.mock_post_repo.find_by_post_id.return_value = _make_post_embedding(embedded_at=yesterday_kst)

        await self.consumer.handle(_make_deleted_message())

        self.mock_post_repo.update_status.assert_not_called()

    async def test_no_embedding_ignored(self):
        """임베딩 레코드 없는 게시글 삭제 — update_status 미호출."""
        self.mock_post_repo.find_by_post_id.return_value = None

        await self.consumer.handle(_make_deleted_message())

        self.mock_post_repo.update_status.assert_not_called()

    async def test_invalid_payload_ignored(self):
        """필수 필드 누락(ownerId) — 예외 전파 없음, update_status 미호출."""
        msg = {
            "eventType": "POST_DELETED",
            "payload": {"postId": str(uuid.uuid4())},
        }
        await self.consumer.handle(msg)

        self.mock_post_repo.update_status.assert_not_called()


class TestPostConsumerPostHardDeleted(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_post_repo = AsyncMock()
        self.mock_profile_repo = AsyncMock()

        mock_db = MagicMock()
        mock_db.__aenter__ = AsyncMock(return_value=MagicMock())
        mock_db.__aexit__ = AsyncMock(return_value=False)

        p_session = patch(f"{_CONSUMER_MODULE}.AsyncSessionLocal", return_value=mock_db)
        p_post = patch(f"{_CONSUMER_MODULE}.PgPostEmbeddingRepository", return_value=self.mock_post_repo)
        p_profile = patch(f"{_CONSUMER_MODULE}.PgUserProfileRepository", return_value=self.mock_profile_repo)
        p_model = patch(f"{_CONSUMER_MODULE}.ModelLoader", return_value=MagicMock())
        p_svc = patch(f"{_CONSUMER_MODULE}.EmbeddingService", return_value=AsyncMock())

        for p in [p_session, p_post, p_profile, p_model, p_svc]:
            p.start()
            self.addCleanup(p.stop)

        self.consumer = PostConsumer()

    async def test_deleted_status_hard_deletes_without_count_update(self):
        """정상 케이스: status=DELETED 게시글 hard delete — delete_by_post_id 호출, record_count 갱신 없음."""
        KST = timezone(timedelta(hours=9))
        self.mock_post_repo.find_by_post_id.return_value = _make_post_embedding(
            embedded_at=datetime.now(KST),
            embedding_status="DELETED",
        )

        await self.consumer.handle(_make_hard_deleted_message())

        self.mock_post_repo.delete_by_post_id.assert_called_once()
        self.mock_profile_repo.sync_count_and_active.assert_not_called()

    async def test_done_status_hard_deletes_and_updates_count(self):
        """엣지케이스: soft delete 누락으로 status=DONE 상태 — delete_by_post_id + record_count 보정."""
        KST = timezone(timedelta(hours=9))
        self.mock_post_repo.find_by_post_id.return_value = _make_post_embedding(
            embedded_at=datetime.now(KST),
            embedding_status="DONE",
        )
        self.mock_post_repo.count_done_by_user_id.return_value = 1

        await self.consumer.handle(_make_hard_deleted_message())

        self.mock_post_repo.delete_by_post_id.assert_called_once()
        self.mock_profile_repo.sync_count_and_active.assert_called_once_with(unittest.mock.ANY, 1)

    async def test_no_embedding_ignored(self):
        """임베딩 레코드 없는 게시글 hard delete — delete_by_post_id 미호출."""
        self.mock_post_repo.find_by_post_id.return_value = None

        await self.consumer.handle(_make_hard_deleted_message())

        self.mock_post_repo.delete_by_post_id.assert_not_called()

    async def test_invalid_payload_ignored(self):
        """필수 필드 누락(authorId) — 예외 전파 없음, delete_by_post_id 미호출."""
        msg = {
            "eventType": "POST_HARD_DELETED",
            "payload": {"postId": str(uuid.uuid4())},
        }
        await self.consumer.handle(msg)

        self.mock_post_repo.delete_by_post_id.assert_not_called()


class TestPostConsumerPostRestored(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_post_repo = AsyncMock()
        self.mock_profile_repo = AsyncMock()

        mock_db = MagicMock()
        mock_db.__aenter__ = AsyncMock(return_value=MagicMock())
        mock_db.__aexit__ = AsyncMock(return_value=False)

        p_session = patch(f"{_CONSUMER_MODULE}.AsyncSessionLocal", return_value=mock_db)
        p_post = patch(f"{_CONSUMER_MODULE}.PgPostEmbeddingRepository", return_value=self.mock_post_repo)
        p_profile = patch(f"{_CONSUMER_MODULE}.PgUserProfileRepository", return_value=self.mock_profile_repo)
        p_model = patch(f"{_CONSUMER_MODULE}.ModelLoader", return_value=MagicMock())
        p_svc = patch(f"{_CONSUMER_MODULE}.EmbeddingService", return_value=AsyncMock())

        for p in [p_session, p_post, p_profile, p_model, p_svc]:
            p.start()
            self.addCleanup(p.stop)

        self.consumer = PostConsumer()

    async def test_deleted_post_restored_to_done(self):
        """status=DELETED 게시글 복구 — status=DONE 갱신 및 record_count 즉시 반영."""
        KST = timezone(timedelta(hours=9))
        self.mock_post_repo.find_by_post_id.return_value = _make_post_embedding(
            embedded_at=datetime.now(KST),
            embedding_status="DELETED",
        )
        self.mock_post_repo.count_done_by_user_id.return_value = 3

        await self.consumer.handle(_make_restored_message())

        self.mock_post_repo.update_status.assert_called_once()
        args = self.mock_post_repo.update_status.call_args.args
        self.assertEqual(args[1], "DONE")
        self.mock_profile_repo.sync_count_and_active.assert_called_once_with(unittest.mock.ANY, 3)

    async def test_non_deleted_status_ignored(self):
        """status=DONE 게시글에 복구 이벤트 — update_status 미호출."""
        KST = timezone(timedelta(hours=9))
        self.mock_post_repo.find_by_post_id.return_value = _make_post_embedding(
            embedded_at=datetime.now(KST),
            embedding_status="DONE",
        )

        await self.consumer.handle(_make_restored_message())

        self.mock_post_repo.update_status.assert_not_called()

    async def test_no_embedding_ignored(self):
        """임베딩 레코드 없는 게시글 복구 — update_status 미호출."""
        self.mock_post_repo.find_by_post_id.return_value = None

        await self.consumer.handle(_make_restored_message())

        self.mock_post_repo.update_status.assert_not_called()

    async def test_invalid_payload_ignored(self):
        """필수 필드 누락(authorId) — 예외 전파 없음, update_status 미호출."""
        msg = {
            "eventType": "POST_RESTORED",
            "payload": {"postId": str(uuid.uuid4())},
        }
        await self.consumer.handle(msg)

        self.mock_post_repo.update_status.assert_not_called()


if __name__ == "__main__":
    unittest.main()
