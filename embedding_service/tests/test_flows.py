"""
check_flows.py 기반 — Flow 1·2·3 단위 테스트.
실제 DB·모델 없이 CI에서 실행 가능.
"""
import uuid
import unittest
import numpy as np

from app.embedding.application.service.embedding_service import EmbeddingService
from app.config.settings import settings
from tests.helpers import FakeModel, FakeBatchRunner, make_profile, new_post_repo, new_profile_repo, FAKE_VECTOR
from app.embedding.domain.algorithm.ema_calculator import average_vectors


class TestFlow1InitialProfile(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.profile_repo = new_profile_repo()
        self.svc = EmbeddingService(new_post_repo(), self.profile_repo, FakeModel(), FakeBatchRunner())

    async def test_record_count_0_and_inactive(self):
        """초기 프로필 — record_count=0, active=False 로 저장된다."""
        user_id = uuid.uuid4()
        await self.svc.create_initial_profile(user_id, ["등산", "자연", "힐링"], "MALE", "20s")

        self.profile_repo.upsert.assert_called_once()
        kw = self.profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["user_id"], user_id)
        self.assertEqual(kw["record_count"], 0)
        self.assertFalse(kw["active"])

    async def test_vector_is_768_dim(self):
        """초기 프로필 — 저장되는 벡터가 768차원이다."""
        await self.svc.create_initial_profile(uuid.uuid4(), ["카공", "취준"], "FEMALE", "20s")
        kw = self.profile_repo.upsert.call_args.kwargs
        self.assertEqual(len(kw["vector"]), 768)
        self.assertEqual(kw["vector"], FAKE_VECTOR)


class TestFlow2EmbedAndStore(unittest.IsolatedAsyncioTestCase):

    async def test_first_post_creates_profile(self):
        """첫 게시글(프로필 없음) — record_count=1, active=False."""
        post_repo = new_post_repo()
        profile_repo = new_profile_repo()
        svc = EmbeddingService(post_repo, profile_repo, FakeModel(), FakeBatchRunner())

        await svc.embed_and_store(uuid.uuid4(), uuid.uuid4(), "한강 자전거", ["한강"])

        post_repo.save.assert_called_once()
        kw = profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["record_count"], 1)
        self.assertFalse(kw["active"])

    async def test_active_flips_at_min_threshold(self):
        """MIN_RECORDS_FOR_MATCHING 도달 시 active=True 전환."""
        post_repo = new_post_repo()
        profile_repo = new_profile_repo()
        profile_repo.find_by_user_id.return_value = make_profile(
            record_count=settings.MIN_RECORDS_FOR_MATCHING - 1
        )
        svc = EmbeddingService(post_repo, profile_repo, FakeModel(), FakeBatchRunner())

        await svc.embed_and_store(uuid.uuid4(), uuid.uuid4(), "새 게시글", [])

        kw = profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["record_count"], settings.MIN_RECORDS_FOR_MATCHING)
        self.assertTrue(kw["active"])

    async def test_below_threshold_stays_inactive(self):
        """MIN - 2 → MIN - 1 상태에서는 active=False 유지."""
        post_repo = new_post_repo()
        profile_repo = new_profile_repo()
        profile_repo.find_by_user_id.return_value = make_profile(
            record_count=settings.MIN_RECORDS_FOR_MATCHING - 2
        )
        svc = EmbeddingService(post_repo, profile_repo, FakeModel(), FakeBatchRunner())

        await svc.embed_and_store(uuid.uuid4(), uuid.uuid4(), "게시글", [])

        kw = profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["record_count"], settings.MIN_RECORDS_FOR_MATCHING - 1)
        self.assertFalse(kw["active"])

    async def test_retry_does_not_update_profile(self):
        """동일 post_id retry — profile_repo.upsert 미호출 (record_count 불변)."""
        post_repo = new_post_repo()
        profile_repo = new_profile_repo()
        post_repo.find_by_post_id.return_value = object()  # 기존 레코드 있음
        svc = EmbeddingService(post_repo, profile_repo, FakeModel(), FakeBatchRunner())

        await svc.embed_and_store(uuid.uuid4(), uuid.uuid4(), "수정된 내용", [])

        post_repo.save.assert_called_once()
        profile_repo.upsert.assert_not_called()

    async def test_existing_profile_preserves_cursor_not_advancing_to_new_post(self):
        """기존 프로필 — last_processed_record_id 유지.

        실시간 경로는 vector를 EMA로 섞지 않고 기존 값을 재사용하므로,
        배치가 이 게시글을 "이미 처리됨"으로 보고 EMA 반영을 영구히 스킵하게 됨.
        """
        post_repo = new_post_repo()
        profile_repo = new_profile_repo()
        old_cursor = uuid.uuid4()
        profile_repo.find_by_user_id.return_value = make_profile(
            record_count=3, last_processed_record_id=old_cursor
        )
        svc = EmbeddingService(post_repo, profile_repo, FakeModel(), FakeBatchRunner())

        new_post_id = uuid.uuid4()
        await svc.embed_and_store(new_post_id, uuid.uuid4(), "새 게시글", [])

        kw = profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["last_processed_record_id"], old_cursor)
        self.assertNotEqual(kw["last_processed_record_id"], new_post_id)

    async def test_first_post_sets_cursor_to_that_post(self):
        """프로필이 없던 유저의 첫 게시글"""
        post_repo = new_post_repo()
        profile_repo = new_profile_repo()
        profile_repo.find_by_user_id.return_value = None
        svc = EmbeddingService(post_repo, profile_repo, FakeModel(), FakeBatchRunner())

        post_id = uuid.uuid4()
        await svc.embed_and_store(post_id, uuid.uuid4(), "첫 게시글", [])

        kw = profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["last_processed_record_id"], post_id)

    async def test_profile_failure_does_not_propagate(self):
        """profile_repo 갱신 실패 시 예외 미전파 — 게시글 임베딩 성공 유지."""
        post_repo = new_post_repo()
        profile_repo = new_profile_repo()
        profile_repo.find_by_user_id.side_effect = Exception("DB 오류")
        svc = EmbeddingService(post_repo, profile_repo, FakeModel(), FakeBatchRunner())

        await svc.embed_and_store(uuid.uuid4(), uuid.uuid4(), "게시글", [])

        post_repo.save.assert_called_once()


class TestRegisterUserInterests(unittest.IsolatedAsyncioTestCase):
    """register_user_interests: vector만 갱신, record_count/active 불변 검증."""

    async def test_calls_update_initial_vector_not_upsert(self):
        """update_initial_vector 호출, upsert 미호출."""
        profile_repo = new_profile_repo()
        profile_repo.find_by_user_id.return_value = make_profile(record_count=2, active=True)
        svc = EmbeddingService(new_post_repo(), profile_repo, FakeModel(), FakeBatchRunner())

        await svc.register_user_interests(uuid.uuid4(), ["카페", "독서"])

        profile_repo.update_initial_vector.assert_called_once()
        profile_repo.upsert.assert_not_called()

    async def test_update_initial_vector_receives_768dim_vector(self):
        """생성된 벡터가 768차원으로 전달된다."""
        profile_repo = new_profile_repo()
        profile_repo.find_by_user_id.return_value = make_profile()
        svc = EmbeddingService(new_post_repo(), profile_repo, FakeModel(), FakeBatchRunner())

        await svc.register_user_interests(uuid.uuid4(), ["혼공", "취준"])

        _, vector = profile_repo.update_initial_vector.call_args.args
        self.assertEqual(len(vector), 768)

    async def test_no_profile_skips(self):
        """프로필 행 없음 — update_initial_vector 미호출."""
        profile_repo = new_profile_repo()
        profile_repo.find_by_user_id.return_value = None
        svc = EmbeddingService(new_post_repo(), profile_repo, FakeModel(), FakeBatchRunner())

        await svc.register_user_interests(uuid.uuid4(), ["태그"])

        profile_repo.update_initial_vector.assert_not_called()

    async def test_missing_gender_skips(self):
        """gender=None — update_initial_vector 미호출."""
        profile_repo = new_profile_repo()
        profile_repo.find_by_user_id.return_value = make_profile(gender=None)
        svc = EmbeddingService(new_post_repo(), profile_repo, FakeModel(), FakeBatchRunner())

        await svc.register_user_interests(uuid.uuid4(), ["태그"])

        profile_repo.update_initial_vector.assert_not_called()

    async def test_missing_birthdate_skips(self):
        """birthdate=None — update_initial_vector 미호출."""
        profile_repo = new_profile_repo()
        p = make_profile()
        p.birthdate = None
        profile_repo.find_by_user_id.return_value = p
        svc = EmbeddingService(new_post_repo(), profile_repo, FakeModel(), FakeBatchRunner())

        await svc.register_user_interests(uuid.uuid4(), ["태그"])

        profile_repo.update_initial_vector.assert_not_called()


class TestFlow3GetProfileVector(unittest.IsolatedAsyncioTestCase):

    async def test_no_profile_returns_none_tuple(self):
        """프로필 없는 유저 — (None, None) 반환."""
        profile_repo = new_profile_repo()
        profile_repo.find_by_user_id.return_value = None
        svc = EmbeddingService(new_post_repo(), profile_repo, FakeModel(), FakeBatchRunner())

        profile, today_vector = await svc.get_profile_vector(uuid.uuid4())

        self.assertIsNone(profile)
        self.assertIsNone(today_vector)

    async def test_no_today_posts_returns_none_today_vector(self):
        """오늘 게시글 없을 때 — today_vector=None."""
        post_repo = new_post_repo()
        profile_repo = new_profile_repo()
        profile_repo.find_by_user_id.return_value = make_profile(record_count=3, active=True)
        post_repo.find_today_vectors.return_value = []
        svc = EmbeddingService(post_repo, profile_repo, FakeModel(), FakeBatchRunner())

        profile, today_vector = await svc.get_profile_vector(uuid.uuid4())

        self.assertIsNotNone(profile)
        self.assertIsNone(today_vector)

    async def test_today_posts_returns_averaged_vector(self):
        """오늘 게시글 있을 때 — today_vector가 average_vectors 결과다."""
        post_repo = new_post_repo()
        profile_repo = new_profile_repo()
        vectors = [[1.0] + [0.0] * 767, [0.0, 1.0] + [0.0] * 766]
        profile_repo.find_by_user_id.return_value = make_profile(record_count=3, active=True)
        post_repo.find_today_vectors.return_value = vectors
        svc = EmbeddingService(post_repo, profile_repo, FakeModel(), FakeBatchRunner())

        _, today_vector = await svc.get_profile_vector(uuid.uuid4())

        assert today_vector is not None
        expected = average_vectors(vectors)
        self.assertTrue(np.allclose(today_vector, expected))


if __name__ == "__main__":
    unittest.main()
