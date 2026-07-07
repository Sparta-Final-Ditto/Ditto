"""
run_batch() 단위 테스트.
AsyncSessionLocal·PgRepository를 mock 처리하여 CI에서 실행 가능.

증분 EMA 경로:
  - profile.last_processed_record_id 있고 vector 있음 → 신규 벡터만 EMA 갱신
  - last_processed_record_id=None 또는 vector=None   → 전체 재계산
"""
import uuid
import unittest
import numpy as np
from unittest.mock import AsyncMock, MagicMock, patch

from app.embedding.infrastructure.batch.batch_embedding import BatchEmbeddingRunner, run_batch
from app.config.settings import settings
from tests.helpers import make_profile, make_vectors, FAKE_VECTOR

_BATCH_MODULE = "app.embedding.infrastructure.batch.batch_embedding"


def make_records(n: int) -> list[tuple[uuid.UUID, list[float]]]:
    """n개의 (post_id, vector) 튜플 목록 생성."""
    return [(uuid.uuid4(), v) for v in make_vectors(n)]


class TestNightlyBatch(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_post_repo = AsyncMock()
        # DB 실측 record_count
        self.mock_post_repo.count_done_by_user_id.return_value = 0
        self.mock_profile_repo = AsyncMock()

        mock_db = MagicMock()
        mock_db.__aenter__ = AsyncMock(return_value=MagicMock())
        mock_db.__aexit__ = AsyncMock(return_value=False)

        p_session = patch(f"{_BATCH_MODULE}.AsyncSessionLocal", return_value=mock_db)
        p_post = patch(f"{_BATCH_MODULE}.PgPostEmbeddingRepository", return_value=self.mock_post_repo)
        p_profile = patch(f"{_BATCH_MODULE}.PgUserProfileRepository", return_value=self.mock_profile_repo)
        p_dlq = patch(f"{_BATCH_MODULE}._reprocess_dlq", new=AsyncMock(return_value=0))
        p_profile_dlq = patch(f"{_BATCH_MODULE}.reprocess_profile_embedding_dlq", new=AsyncMock(return_value=0))
        p_producer = patch(f"{_BATCH_MODULE}.ProfileEmbeddingProducer", autospec=True)

        self.mock_producer = p_producer.start()

        self.addCleanup(p_session.stop)
        self.addCleanup(p_post.stop)
        self.addCleanup(p_profile.stop)
        self.addCleanup(p_dlq.stop)
        self.addCleanup(p_profile_dlq.stop)
        self.addCleanup(p_producer.stop)

        p_session.start()
        p_post.start()
        p_profile.start()
        p_dlq.start()
        p_profile_dlq.start()

    # ── 전체 재계산 경로 (last_processed_record_id=None) ─────────────────────

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
        self.mock_post_repo.find_done_vectors_after.return_value = make_records(3)
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        self.assertEqual(self.mock_profile_repo.upsert.call_count, len(user_ids))

    async def test_user_with_no_vectors_is_skipped(self):
        """DONE 벡터 없는 유저 — upsert 미호출."""
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_done_vectors_after.return_value = []
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        self.mock_profile_repo.upsert.assert_not_called()

    async def test_active_flag_at_min_threshold(self):
        """record_count == MIN_RECORDS_FOR_MATCHING 도달 시 active=True."""
        min_count = settings.MIN_RECORDS_FOR_MATCHING
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_done_vectors_after.return_value = make_records(min_count)
        self.mock_post_repo.count_done_by_user_id.return_value = min_count
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
        self.mock_post_repo.find_done_vectors_after.return_value = make_records(min_count - 1)
        self.mock_post_repo.count_done_by_user_id.return_value = min_count - 1
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

        call_count = 0

        async def vectors_by_user(uid, after_post_id):
            nonlocal call_count
            call_count += 1
            if uid == user_a:
                raise Exception("유저 A DB 오류")
            return make_records(2)

        self.mock_post_repo.find_done_vectors_after.side_effect = vectors_by_user
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        self.mock_profile_repo.upsert.assert_called_once()
        self.assertEqual(self.mock_profile_repo.upsert.call_args.kwargs["user_id"], user_b)

    async def test_single_vector_stored_as_is(self):
        """벡터가 1개인 유저 — EMA 루프 없이 그 벡터 그대로 저장."""
        records = make_records(1)
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_done_vectors_after.return_value = records
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        kw = self.mock_profile_repo.upsert.call_args.kwargs
        self.assertTrue(np.allclose(kw["vector"], records[0][1]))

    async def test_last_processed_record_id_updated(self):
        """배치 후 last_processed_record_id가 마지막 post_id로 갱신된다."""
        records = make_records(3)
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_done_vectors_after.return_value = records
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        kw = self.mock_profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["last_processed_record_id"], records[-1][0])

    async def test_no_profile_falls_back_to_full_recalc(self):
        """프로필 행 없음(DB 초기화 등) → 전체 재계산 후 upsert 호출."""
        records = make_records(3)
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_done_vectors_after.return_value = records
        self.mock_post_repo.count_done_by_user_id.return_value = 3
        self.mock_profile_repo.find_by_user_id.return_value = None  # 프로필 없음

        await run_batch()

        self.mock_profile_repo.upsert.assert_called_once()
        kw = self.mock_profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["record_count"], 3)
        self.assertEqual(kw["last_processed_record_id"], records[-1][0])

    # ── 증분 처리 경로 (last_processed_record_id 있음) ───────────────────────

    async def test_incremental_uses_existing_ema_as_base(self):
        """증분 경로: 기존 profile.vector를 EMA 시작점으로 사용한다."""
        last_post_id = uuid.uuid4()
        profile = make_profile(
            record_count=5,
            active=True,
            last_processed_record_id=last_post_id,
        )
        new_records = make_records(2)

        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_done_vectors_after.return_value = new_records
        self.mock_profile_repo.find_by_user_id.return_value = profile

        await run_batch()

        _, call_kwargs = self.mock_post_repo.find_done_vectors_after.call_args
        # after_post_id가 last_processed_record_id로 전달됐는지 확인
        self.assertEqual(call_kwargs.get("after_post_id") or
                         self.mock_post_repo.find_done_vectors_after.call_args.args[1],
                         last_post_id)

    async def test_incremental_record_count_uses_db_authoritative_count(self):
        last_post_id = uuid.uuid4()
        profile = make_profile(record_count=5, last_processed_record_id=last_post_id)
        new_records = make_records(3)

        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_done_vectors_after.return_value = new_records
        self.mock_post_repo.count_done_by_user_id.return_value = 9
        self.mock_profile_repo.find_by_user_id.return_value = profile

        await run_batch()

        kw = self.mock_profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["record_count"], 9)

    async def test_incremental_no_new_vectors_skips_upsert(self):
        """증분 경로: 신규 벡터 없음 → upsert 미호출."""
        last_post_id = uuid.uuid4()
        profile = make_profile(record_count=5, last_processed_record_id=last_post_id)

        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_done_vectors_after.return_value = []
        self.mock_profile_repo.find_by_user_id.return_value = profile

        await run_batch()

        self.mock_profile_repo.upsert.assert_not_called()

    async def test_incremental_last_processed_record_id_updated_to_latest(self):
        """증분 경로: last_processed_record_id가 신규 레코드 중 마지막 post_id로 갱신된다."""
        last_post_id = uuid.uuid4()
        profile = make_profile(record_count=5, last_processed_record_id=last_post_id)
        new_records = make_records(3)

        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_done_vectors_after.return_value = new_records
        self.mock_profile_repo.find_by_user_id.return_value = profile

        await run_batch()

        kw = self.mock_profile_repo.upsert.call_args.kwargs
        self.assertEqual(kw["last_processed_record_id"], new_records[-1][0])

    # ── 이벤트 발행 (profile-embedding-updated / bulk-completed) ───────────────

    async def test_publishes_delta_event_when_under_threshold(self):
        """변경 인원이 threshold 이하 — 유저별 PROFILE_EMBEDDING_UPDATED 발행, bulk는 미발행."""
        user_id = uuid.uuid4()
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [user_id]
        self.mock_post_repo.find_done_vectors_after.return_value = make_records(2)
        self.mock_post_repo.count_done_by_user_id.return_value = 2
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        self.mock_producer.publish_profile_updated.assert_awaited_once()
        args = self.mock_producer.publish_profile_updated.call_args.args
        self.assertEqual(args[0], user_id)
        self.mock_producer.publish_bulk_completed.assert_not_awaited()

    async def test_publishes_bulk_completed_when_over_threshold(self):
        """변경 인원이 threshold 초과 — BULK_COMPLETED만 발행, 개별 delta는 미발행."""
        user_a, user_b = uuid.uuid4(), uuid.uuid4()
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [user_a, user_b]
        self.mock_post_repo.find_done_vectors_after.return_value = make_records(1)
        self.mock_post_repo.count_done_by_user_id.return_value = 1
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        with patch.object(settings, "PROFILE_SYNC_BULK_THRESHOLD", 1):
            await run_batch()

        self.mock_producer.publish_bulk_completed.assert_awaited_once()
        kw = self.mock_producer.publish_bulk_completed.call_args.kwargs
        self.assertEqual(kw["batch_type"], "DAILY")
        self.assertEqual(kw["total_updated"], 2)
        self.mock_producer.publish_profile_updated.assert_not_awaited()

    async def test_no_event_published_when_nothing_changed(self):
        """변경 인원 0명 — 어떤 이벤트도 발행하지 않는다."""
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = [uuid.uuid4()]
        self.mock_post_repo.find_done_vectors_after.return_value = []
        self.mock_profile_repo.find_by_user_id.return_value = make_profile()

        await run_batch()

        self.mock_producer.publish_profile_updated.assert_not_awaited()
        self.mock_producer.publish_bulk_completed.assert_not_awaited()

    async def test_profile_embedding_dlq_reprocessed_before_processing(self):
        """profile-embedding DLQ 재처리가 배치 시작 시 호출된다 (post-events-dlq와 동일한 패턴)."""
        self.mock_post_repo.reset_failed_to_done.return_value = 0
        self.mock_post_repo.find_all_user_ids_with_done_embeddings.return_value = []

        with patch(f"{_BATCH_MODULE}.reprocess_profile_embedding_dlq", new=AsyncMock(return_value=3)) as mocked:
            await run_batch()
            mocked.assert_awaited_once()


class TestBatchEmbeddingRunner(unittest.IsolatedAsyncioTestCase):

    async def test_run_daily_delegates_to_run_batch(self):
        with patch(f"{_BATCH_MODULE}.run_batch", new=AsyncMock()) as mocked:
            await BatchEmbeddingRunner().run_daily()
            mocked.assert_awaited_once()

    async def test_run_monthly_delegates_to_run_monthly_batch(self):
        with patch(f"{_BATCH_MODULE}.run_monthly_batch", new=AsyncMock()) as mocked:
            await BatchEmbeddingRunner().run_monthly()
            mocked.assert_awaited_once()


if __name__ == "__main__":
    unittest.main()
