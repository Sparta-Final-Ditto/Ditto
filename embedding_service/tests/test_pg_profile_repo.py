"""
PgUserProfileRepository 단위 테스트 — AsyncSession mock, 실제 DB 없음.
"""
import uuid
import unittest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

from app.embedding.infrastructure.repository.pg_user_profile_repository import (
    PgUserProfileRepository,
)
from tests.helpers import FAKE_VECTOR


def _make_profile_row(**kwargs):
    row = MagicMock()
    row.user_id = kwargs.get("user_id", uuid.uuid4())
    row.vector = kwargs.get("vector", FAKE_VECTOR)
    row.record_count = kwargs.get("record_count", 5)
    row.active = kwargs.get("active", True)
    row.last_processed_record_id = kwargs.get("last_processed_record_id", None)
    row.updated_at = kwargs.get("updated_at", datetime.now(timezone.utc))
    row.created_at = kwargs.get("created_at", datetime.now(timezone.utc))
    return row


class TestPgUserProfileRepository(unittest.IsolatedAsyncioTestCase):

    # ── find_by_user_id ───────────────────────────────────────────────────────

    async def test_find_by_user_id_returns_domain(self):
        """존재하는 유저 — UserProfile 도메인 반환."""
        row = _make_profile_row()
        db = AsyncMock()
        db.get.return_value = row
        repo = PgUserProfileRepository(db)

        result = await repo.find_by_user_id(row.user_id)

        self.assertIsNotNone(result)
        self.assertEqual(result.user_id, row.user_id)
        self.assertTrue(result.active)

    async def test_find_by_user_id_not_found(self):
        """미존재 유저 — None 반환."""
        db = AsyncMock()
        db.get.return_value = None
        repo = PgUserProfileRepository(db)

        result = await repo.find_by_user_id(uuid.uuid4())

        self.assertIsNone(result)

    # ── upsert — update path ──────────────────────────────────────────────────

    async def test_upsert_updates_existing_row(self):
        """row 존재 시 — 필드 갱신 + commit."""
        row = _make_profile_row(record_count=3, active=False)
        db = AsyncMock()
        db.get.return_value = row
        repo = PgUserProfileRepository(db)

        new_vector = [0.5] * 768
        await repo.upsert(row.user_id, new_vector, record_count=10, active=True)

        self.assertEqual(row.vector, new_vector)
        self.assertEqual(row.record_count, 10)
        self.assertTrue(row.active)
        db.commit.assert_awaited_once()

    async def test_upsert_inserts_new_row(self):
        """row 없을 때 — db.add() + commit 호출."""
        db = AsyncMock()
        db.get.return_value = None
        db.add = MagicMock()
        repo = PgUserProfileRepository(db)

        await repo.upsert(uuid.uuid4(), FAKE_VECTOR, record_count=1, active=True)

        db.add.assert_called_once()
        db.commit.assert_awaited_once()

    async def test_upsert_with_last_processed_record_id(self):
        """last_processed_record_id 갱신 확인."""
        row = _make_profile_row()
        db = AsyncMock()
        db.get.return_value = row
        repo = PgUserProfileRepository(db)

        record_id = uuid.uuid4()
        await repo.upsert(row.user_id, FAKE_VECTOR, 5, True, last_processed_record_id=record_id)

        self.assertEqual(row.last_processed_record_id, record_id)

    # ── find_all_user_ids ─────────────────────────────────────────────────────

    async def test_find_all_user_ids(self):
        """전체 유저 ID 목록 반환."""
        uid1, uid2 = uuid.uuid4(), uuid.uuid4()
        db = AsyncMock()
        mock_result = MagicMock()
        mock_result.all.return_value = [(uid1,), (uid2,)]
        db.execute.return_value = mock_result
        repo = PgUserProfileRepository(db)

        result = await repo.find_all_user_ids()

        self.assertIn(uid1, result)
        self.assertIn(uid2, result)

    async def test_find_all_user_ids_empty(self):
        """프로필 없을 때 — 빈 리스트."""
        db = AsyncMock()
        mock_result = MagicMock()
        mock_result.all.return_value = []
        db.execute.return_value = mock_result
        repo = PgUserProfileRepository(db)

        result = await repo.find_all_user_ids()

        self.assertEqual(result, [])

    # ── find_active_user_ids ──────────────────────────────────────────────────

    async def test_find_active_user_ids(self):
        """active=True, deleted_at=None 필터링 — 활성 유저 ID 반환."""
        uid = uuid.uuid4()
        db = AsyncMock()
        mock_result = MagicMock()
        mock_result.all.return_value = [(uid,)]
        db.execute.return_value = mock_result
        repo = PgUserProfileRepository(db)

        result = await repo.find_active_user_ids()

        self.assertEqual(result, [uid])

    async def test_find_active_user_ids_empty(self):
        """활성 유저 없음 — 빈 리스트."""
        db = AsyncMock()
        mock_result = MagicMock()
        mock_result.all.return_value = []
        db.execute.return_value = mock_result
        repo = PgUserProfileRepository(db)

        result = await repo.find_active_user_ids()

        self.assertEqual(result, [])


    # ── update_initial_vector ─────────────────────────────────────────────────

    async def test_update_initial_vector_updates_only_vector(self):
        """row 존재 시 — vector만 갱신, record_count/active는 건드리지 않는다."""
        row = _make_profile_row(record_count=2, active=True)
        original_count = row.record_count
        original_active = row.active
        db = AsyncMock()
        db.get.return_value = row
        repo = PgUserProfileRepository(db)

        new_vector = [0.1] * 768
        await repo.update_initial_vector(row.user_id, new_vector)

        self.assertEqual(row.vector, new_vector)
        self.assertEqual(row.record_count, original_count)  # 불변
        self.assertEqual(row.active, original_active)       # 불변
        db.commit.assert_awaited_once()

    async def test_update_initial_vector_row_not_found_is_noop(self):
        """row 없을 때 — 예외 없이 무시."""
        db = AsyncMock()
        db.get.return_value = None
        repo = PgUserProfileRepository(db)

        await repo.update_initial_vector(uuid.uuid4(), [0.1] * 768)

        db.commit.assert_not_awaited()

    # ── init_user_profile ─────────────────────────────────────────────────────

    async def test_init_user_profile_creates_stub(self):
        """row 없을 때 — vector=None, record_count=0, active=False stub 생성."""
        from datetime import date
        db = AsyncMock()
        db.get.return_value = None
        db.add = MagicMock()
        repo = PgUserProfileRepository(db)

        uid = uuid.uuid4()
        bd = date(1999, 5, 20)
        await repo.init_user_profile(uid, "FEMALE", bd)

        db.add.assert_called_once()
        added = db.add.call_args.args[0]
        self.assertIsNone(added.vector)
        self.assertEqual(added.record_count, 0)
        self.assertFalse(added.active)
        self.assertEqual(added.gender, "FEMALE")
        self.assertEqual(added.birthdate, bd)
        db.commit.assert_awaited_once()

    async def test_init_user_profile_updates_existing_metadata_only(self):
        """row 존재 시 — gender/birthdate만 갱신, vector/record_count/active 불변."""
        from datetime import date
        row = _make_profile_row(record_count=3, active=True)
        row.vector = FAKE_VECTOR
        original_count = row.record_count
        original_active = row.active
        original_vector = row.vector
        db = AsyncMock()
        db.get.return_value = row
        repo = PgUserProfileRepository(db)

        bd = date(1995, 3, 15)
        await repo.init_user_profile(row.user_id, "MALE", bd)

        self.assertEqual(row.gender, "MALE")
        self.assertEqual(row.birthdate, bd)
        self.assertEqual(row.record_count, original_count)  # 불변
        self.assertEqual(row.active, original_active)       # 불변
        self.assertEqual(row.vector, original_vector)       # 불변
        db.commit.assert_awaited_once()


if __name__ == "__main__":
    unittest.main()
