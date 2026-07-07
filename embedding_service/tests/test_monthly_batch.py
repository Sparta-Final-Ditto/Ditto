"""
run_monthly_batch() 단위 테스트.
AsyncSessionLocal·PgRepository를 mock 처리하여 CI에서 실행 가능.

일배치와의 핵심 차이: 월배치는 변경 인원과 무관하게 항상
PROFILE_EMBEDDING_BULK_COMPLETED(batchType=MONTHLY)만 발행한다.
"""
import uuid
import unittest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch

from app.embedding.infrastructure.batch.batch_embedding import run_monthly_batch
from tests.helpers import make_vectors

_BATCH_MODULE = "app.embedding.infrastructure.batch.batch_embedding"


def make_monthly_records(n: int) -> list[tuple[uuid.UUID, list[float], datetime]]:
    """n개의 (post_id, vector, embedded_at) 튜플 목록 생성."""
    now = datetime(2026, 1, 1, tzinfo=timezone.utc)
    return [(uuid.uuid4(), v, now) for v in make_vectors(n)]


class TestMonthlyBatch(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_post_repo = AsyncMock()
        self.mock_profile_repo = AsyncMock()

        mock_db = MagicMock()
        mock_db.__aenter__ = AsyncMock(return_value=MagicMock())
        mock_db.__aexit__ = AsyncMock(return_value=False)

        p_session = patch(f"{_BATCH_MODULE}.AsyncSessionLocal", return_value=mock_db)
        p_post = patch(f"{_BATCH_MODULE}.PgPostEmbeddingRepository", return_value=self.mock_post_repo)
        p_profile = patch(f"{_BATCH_MODULE}.PgUserProfileRepository", return_value=self.mock_profile_repo)
        p_producer = patch(f"{_BATCH_MODULE}.ProfileEmbeddingProducer", autospec=True)

        self.mock_producer = p_producer.start()

        self.addCleanup(p_session.stop)
        self.addCleanup(p_post.stop)
        self.addCleanup(p_profile.stop)
        self.addCleanup(p_producer.stop)

        p_session.start()
        p_post.start()
        p_profile.start()

    async def test_upsert_called_for_each_user_with_records(self):
        """게시글 있는 유저마다 upsert가 호출된다."""
        user_ids = [uuid.uuid4(), uuid.uuid4()]
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = user_ids
        self.mock_post_repo.find_all_done_for_monthly_batch.return_value = make_monthly_records(3)

        await run_monthly_batch()

        self.assertEqual(self.mock_profile_repo.upsert.call_count, len(user_ids))

    async def test_user_with_no_records_is_skipped(self):
        """게시글 없는 유저 — upsert 미호출."""
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_all_done_for_monthly_batch.return_value = []

        await run_monthly_batch()

        self.mock_profile_repo.upsert.assert_not_called()

    async def test_one_user_failure_does_not_stop_others(self):
        """한 유저 처리 중 예외 발생 — 다른 유저는 정상 처리."""
        user_a, user_b = uuid.uuid4(), uuid.uuid4()
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [user_a, user_b]

        async def records_by_user(uid):
            if uid == user_a:
                raise Exception("유저 A DB 오류")
            return make_monthly_records(2)

        self.mock_post_repo.find_all_done_for_monthly_batch.side_effect = records_by_user

        await run_monthly_batch()

        self.mock_profile_repo.upsert.assert_called_once()
        self.assertEqual(self.mock_profile_repo.upsert.call_args.kwargs["user_id"], user_b)

    async def test_always_publishes_bulk_completed_regardless_of_size(self):
        """월배치는 변경 인원과 무관하게 항상 BULK_COMPLETED(batchType=MONTHLY)만 발행한다."""
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_all_done_for_monthly_batch.return_value = make_monthly_records(2)

        await run_monthly_batch()

        self.mock_producer.publish_bulk_completed.assert_awaited_once()
        kw = self.mock_producer.publish_bulk_completed.call_args.kwargs
        self.assertEqual(kw["batch_type"], "MONTHLY")
        self.assertEqual(kw["total_updated"], 1)
        self.mock_producer.publish_profile_updated.assert_not_awaited()

    async def test_publishes_bulk_completed_even_when_no_users_updated(self):
        """유저 전부 스킵돼도(변경 0명) BULK_COMPLETED는 여전히 발행된다 — 일배치와의 차이점."""
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_all_done_for_monthly_batch.return_value = []

        await run_monthly_batch()

        self.mock_producer.publish_bulk_completed.assert_awaited_once()
        kw = self.mock_producer.publish_bulk_completed.call_args.kwargs
        self.assertEqual(kw["total_updated"], 0)
        self.assertEqual(kw["total_skipped"], 1)


if __name__ == "__main__":
    unittest.main()
