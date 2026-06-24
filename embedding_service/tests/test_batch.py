"""
check_batch.py 기반 — run_batch() 단위 테스트.
AsyncSessionLocal·PgRepository를 mock 처리하여 CI에서 실행 가능.
"""
import uuid
import unittest
import numpy as np
from unittest.mock import AsyncMock, MagicMock, patch

from app.embedding.infrastructure.batch.batch_embedding import run_batch
from app.config.settings import settings
from tests.helpers import make_profile, make_vectors

_BATCH_MODULE = "app.embedding.infrastructure.batch.batch_embedding"


class TestNightlyBatch(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_post_repo = AsyncMock()
        self.mock_profile_repo = AsyncMock()

        mock_db = MagicMock()
        mock_db.__aenter__ = AsyncMock(return_value=MagicMock())
        mock_db.__aexit__ = AsyncMock(return_value=False)

        p_session = patch(f"{_BATCH_MODULE}.AsyncSessionLocal", return_value=mock_db)
        p_post = patch(f"{_BATCH_MODULE}.PgPostEmbeddingRepository", return_value=self.mock_post_repo)
        p_profile = patch(f"{_BATCH_MODULE}.PgUserProfileRepository", return_value=self.mock_profile_repo)

        self.addCleanup(p_session.stop)
        self.addCleanup(p_post.stop)
        self.addCleanup(p_profile.stop)

        p_session.start()
        p_post.start()
        p_profile.start()

    async def test_failed_reset_called_before_ema(self):
        """FAILED → DONE 복구가 EMA 재계산보다 먼저 호출된다."""
        self.mock_post_repo.reset_failed_to_done.return_value = 2
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = []

        await run_batch()

        self.mock_post_repo.reset_failed_to_done.assert_called_once()

    async def test_ema_upsert_called_for_each_user(self):
        """유저 수만큼 profile_repo.upsert가 호출된다."""
        user_ids = [uuid.uuid4(), uuid.uuid4()]
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = user_ids
        self.mock_post_repo.find_all_done_vectors_ordered.return_value = make_vectors(3)
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        self.assertEqual(self.mock_profile_repo.upsert.call_count, len(user_ids))

    async def test_user_with_no_vectors_is_skipped(self):
        """DONE 벡터 없는 유저 — upsert 미호출."""
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_all_done_vectors_ordered.return_value = []

        await run_batch()

        self.mock_profile_repo.upsert.assert_not_called()

    async def test_active_flag_at_min_threshold(self):
        """record_count == MIN_RECORDS_FOR_MATCHING 도달 시 active=True."""
        min_count = settings.MIN_RECORDS_FOR_MATCHING
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_all_done_vectors_ordered.return_value = make_vectors(min_count)
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        kw = self.mock_profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["record_count"], min_count)
        self.assertTrue(kw["active"])

    async def test_active_false_below_threshold(self):
        """record_count < MIN_RECORDS_FOR_MATCHING 일 때 active=False."""
        min_count = settings.MIN_RECORDS_FOR_MATCHING
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_all_done_vectors_ordered.return_value = make_vectors(min_count - 1)
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        kw = self.mock_profile_repo.upsert.call_args.kwargs
        self.assertFalse(kw["active"])

    async def test_one_user_failure_does_not_stop_others(self):
        """한 유저 처리 중 예외 발생 → 다른 유저는 정상 처리."""
        user_a = uuid.uuid4()
        user_b = uuid.uuid4()
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [user_a, user_b]
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        async def vectors_by_user(uid):
            if uid == user_a:
                raise Exception("유저 A DB 오류")
            return make_vectors(2)

        self.mock_post_repo.find_all_done_vectors_ordered.side_effect = vectors_by_user

        await run_batch()

        self.mock_profile_repo.upsert.assert_called_once()
        self.assertEqual(self.mock_profile_repo.upsert.call_args.kwargs["user_id"], user_b)

    async def test_single_vector_stored_as_is(self):
        """벡터가 1개인 유저 — EMA 루프 없이 그 벡터 그대로 저장."""
        single_vector = make_vectors(1)
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_all_done_vectors_ordered.return_value = single_vector
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        kw = self.mock_profile_repo.upsert.call_args.kwargs
        self.assertTrue(np.allclose(kw["vector"], single_vector[0]))


if __name__ == "__main__":
    unittest.main()
