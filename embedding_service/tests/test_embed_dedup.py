"""
check_embed_dedup.py 기반 — embed_and_store is_new/dedup 단위 테스트.
실제 DB·모델 없이 CI에서 실행 가능.
"""
import uuid
import unittest

from app.embedding.application.service.embedding_service import EmbeddingService
from app.config.settings import settings
from tests.helpers import FakeModel, FakeBatchRunner, make_profile, new_post_repo, new_profile_repo


class TestEmbedAndStoreDedup(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.post_repo = new_post_repo()
        self.profile_repo = new_profile_repo()
        self.svc = EmbeddingService(self.post_repo, self.profile_repo, FakeModel(), FakeBatchRunner())

    async def test_case1_new_post_increments_count(self):
        """Case 1: 신규 post_id → record_count 증가, save() 호출."""
        self.post_repo.find_by_post_id.return_value = None
        self.profile_repo.find_by_user_id.return_value = None

        await self.svc.embed_and_store(uuid.uuid4(), uuid.uuid4(), "첫 번째 게시글", ["여행", "힐링"])

        self.post_repo.save.assert_called_once()
        kw = self.profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["record_count"], 1)
        self.assertFalse(kw["active"])
        self.assertEqual(len(kw["vector"]), 768)

    async def test_case2_retry_save_called_profile_not_updated(self):
        """Case 2: 동일 post_id retry — save()는 호출, profile upsert는 미호출."""
        self.post_repo.find_by_post_id.return_value = object()  # 기존 레코드

        await self.svc.embed_and_store(uuid.uuid4(), uuid.uuid4(), "수정된 게시글", ["등산"])

        self.post_repo.save.assert_called_once()
        self.profile_repo.upsert.assert_not_called()

    async def test_case3_active_flips_exactly_at_min(self):
        """Case 3: record_count가 정확히 MIN_RECORDS_FOR_MATCHING 도달 시 active=True."""
        min_count = settings.MIN_RECORDS_FOR_MATCHING
        self.post_repo.find_by_post_id.return_value = None
        self.profile_repo.find_by_user_id.return_value = make_profile(record_count=min_count - 1)

        await self.svc.embed_and_store(uuid.uuid4(), uuid.uuid4(), "게시글", [])

        kw = self.profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["record_count"], min_count)
        self.assertTrue(kw["active"])

    async def test_case3_active_false_one_below_threshold(self):
        """Case 3: MIN - 1 미만일 때 active=False 유지."""
        min_count = settings.MIN_RECORDS_FOR_MATCHING
        self.post_repo.find_by_post_id.return_value = None
        self.profile_repo.find_by_user_id.return_value = make_profile(record_count=min_count - 2)

        await self.svc.embed_and_store(uuid.uuid4(), uuid.uuid4(), "게시글", [])

        kw = self.profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["record_count"], min_count - 1)
        self.assertFalse(kw["active"])

    async def test_case4_multiple_retries_do_not_increment(self):
        """Case 4: 동일 post_id 3회 retry — save 3회, profile upsert 0회."""
        post_id = uuid.uuid4()
        user_id = uuid.uuid4()
        self.post_repo.find_by_post_id.return_value = object()

        for i in range(3):
            await self.svc.embed_and_store(post_id, user_id, f"retry #{i}", [])

        self.assertEqual(self.post_repo.save.call_count, 3)
        self.profile_repo.upsert.assert_not_called()

    async def test_new_then_retry_then_new_sequence(self):
        """신규 → retry → 새 신규 순서에서 record_count가 정확하게 증가한다."""
        user_id = uuid.uuid4()
        post_id_a = uuid.uuid4()
        post_id_b = uuid.uuid4()

        # 1. 신규 A
        self.post_repo.find_by_post_id.return_value = None
        self.profile_repo.find_by_user_id.return_value = None
        await self.svc.embed_and_store(post_id_a, user_id, "첫 게시글", [])
        self.assertEqual(self.profile_repo.upsert.call_args.kwargs["record_count"], 1)

        # 2. A retry
        self.post_repo.find_by_post_id.return_value = object()
        self.profile_repo.upsert.reset_mock()
        await self.svc.embed_and_store(post_id_a, user_id, "수정", [])
        self.profile_repo.upsert.assert_not_called()

        # 3. 신규 B
        self.post_repo.find_by_post_id.return_value = None
        self.profile_repo.find_by_user_id.return_value = make_profile(record_count=1)
        await self.svc.embed_and_store(post_id_b, user_id, "두 번째 게시글", [])
        self.assertEqual(self.profile_repo.upsert.call_args.kwargs["record_count"], 2)


if __name__ == "__main__":
    unittest.main()
