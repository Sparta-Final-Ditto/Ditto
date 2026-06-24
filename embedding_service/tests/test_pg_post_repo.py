"""
PgPostEmbeddingRepository 단위 테스트 — AsyncSession mock, 실제 DB 없음.
"""
import uuid
import unittest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock

from app.embedding.infrastructure.repository.pg_post_embedding_repository import (
    PgPostEmbeddingRepository,
)
from tests.helpers import FAKE_VECTOR


def _make_row(**kwargs):
    row = MagicMock()
    row.post_id = kwargs.get("post_id", uuid.uuid4())
    row.user_id = kwargs.get("user_id", uuid.uuid4())
    row.vector = kwargs.get("vector", FAKE_VECTOR)
    row.embedding_status = kwargs.get("embedding_status", "DONE")
    row.embedded_at = kwargs.get("embedded_at", datetime.now(timezone.utc))
    row.created_at = kwargs.get("created_at", datetime.now(timezone.utc))
    return row


def _make_db(execute_result=None, get_result=None):
    db = AsyncMock()
    mock_result = MagicMock()
    db.execute.return_value = mock_result
    db.get.return_value = get_result
    mock_result.scalar_one_or_none.return_value = execute_result
    if execute_result is not None:
        mock_result.scalars.return_value.all.return_value = (
            execute_result if isinstance(execute_result, list) else [execute_result]
        )
        mock_result.all.return_value = execute_result
    return db, mock_result


class TestPgPostEmbeddingRepository(unittest.IsolatedAsyncioTestCase):

    # ── save ──────────────────────────────────────────────────────────────────

    async def test_save_executes_upsert(self):
        """save() — execute + commit 각 1회 호출."""
        db, _ = _make_db()
        repo = PgPostEmbeddingRepository(db)

        await repo.save(uuid.uuid4(), uuid.uuid4(), FAKE_VECTOR)

        db.execute.assert_awaited_once()
        db.commit.assert_awaited_once()

    # ── find_by_post_id ───────────────────────────────────────────────────────

    async def test_find_by_post_id_returns_domain(self):
        """존재하는 post_id — PostEmbedding 도메인 객체 반환."""
        row = _make_row()
        db, _ = _make_db(execute_result=row)
        repo = PgPostEmbeddingRepository(db)

        result = await repo.find_by_post_id(row.post_id)

        self.assertIsNotNone(result)
        self.assertEqual(result.post_id, row.post_id)
        self.assertEqual(result.embedding_status, "DONE")

    async def test_find_by_post_id_returns_none(self):
        """미존재 post_id — None 반환."""
        db, _ = _make_db(execute_result=None)
        repo = PgPostEmbeddingRepository(db)

        result = await repo.find_by_post_id(uuid.uuid4())

        self.assertIsNone(result)

    # ── find_all_by_user_id ───────────────────────────────────────────────────

    async def test_find_all_by_user_id_returns_list(self):
        """유저의 게시글 임베딩 전체 반환."""
        rows = [_make_row(), _make_row()]
        db, _ = _make_db(execute_result=rows)
        repo = PgPostEmbeddingRepository(db)

        result = await repo.find_all_by_user_id(uuid.uuid4())

        self.assertEqual(len(result), 2)

    async def test_find_all_by_user_id_empty(self):
        """게시글 없는 유저 — 빈 리스트 반환."""
        db, mock_result = _make_db()
        mock_result.scalars.return_value.all.return_value = []
        repo = PgPostEmbeddingRepository(db)

        result = await repo.find_all_by_user_id(uuid.uuid4())

        self.assertEqual(result, [])

    # ── update_status ─────────────────────────────────────────────────────────

    async def test_update_status_found(self):
        """row 존재 시 — status 갱신 + commit 호출."""
        row = _make_row(embedding_status="PENDING")
        db, _ = _make_db(execute_result=row)
        repo = PgPostEmbeddingRepository(db)

        await repo.update_status(row.post_id, "FAILED")

        self.assertEqual(row.embedding_status, "FAILED")
        db.commit.assert_awaited_once()

    async def test_update_status_not_found(self):
        """row 미존재 시 — commit 미호출."""
        db, _ = _make_db(execute_result=None)
        repo = PgPostEmbeddingRepository(db)

        await repo.update_status(uuid.uuid4(), "FAILED")

        db.commit.assert_not_awaited()

    # ── find_today_vectors ────────────────────────────────────────────────────

    async def test_find_today_vectors_returns_list(self):
        """오늘 벡터 목록 반환 — row[0] 추출."""
        db, mock_result = _make_db()
        mock_result.all.return_value = [(FAKE_VECTOR,), (FAKE_VECTOR,)]
        repo = PgPostEmbeddingRepository(db)

        result = await repo.find_today_vectors(uuid.uuid4())

        self.assertEqual(len(result), 2)
        self.assertEqual(len(result[0]), 768)

    async def test_find_today_vectors_empty(self):
        db, mock_result = _make_db()
        mock_result.all.return_value = []
        repo = PgPostEmbeddingRepository(db)

        result = await repo.find_today_vectors(uuid.uuid4())

        self.assertEqual(result, [])

    # ── find_all_done_vectors_ordered ─────────────────────────────────────────

    async def test_find_all_done_vectors_ordered(self):
        """DONE 상태 벡터 전체 반환."""
        db, mock_result = _make_db()
        mock_result.all.return_value = [(FAKE_VECTOR,)]
        repo = PgPostEmbeddingRepository(db)

        result = await repo.find_all_done_vectors_ordered(uuid.uuid4())

        self.assertEqual(len(result), 1)

    # ── find_all_user_ids_with_done_embeddings ────────────────────────────────

    async def test_find_all_user_ids_with_done(self):
        """DONE 임베딩 보유 유저 ID 목록 반환."""
        uid1, uid2 = uuid.uuid4(), uuid.uuid4()
        db, mock_result = _make_db()
        mock_result.all.return_value = [(uid1,), (uid2,)]
        repo = PgPostEmbeddingRepository(db)

        result = await repo.find_all_user_ids_with_done_embeddings()

        self.assertIn(uid1, result)
        self.assertIn(uid2, result)

    # ── reset_failed_to_done ──────────────────────────────────────────────────

    async def test_reset_failed_to_done_returns_rowcount(self):
        """FAILED → DONE 리셋 — rowcount 반환."""
        db, mock_result = _make_db()
        mock_result.rowcount = 3
        repo = PgPostEmbeddingRepository(db)

        count = await repo.reset_failed_to_done()

        self.assertEqual(count, 3)
        db.commit.assert_awaited_once()

    async def test_reset_failed_to_done_zero(self):
        """리셋 대상 없음 — 0 반환."""
        db, mock_result = _make_db()
        mock_result.rowcount = 0
        repo = PgPostEmbeddingRepository(db)

        count = await repo.reset_failed_to_done()

        self.assertEqual(count, 0)


if __name__ == "__main__":
    unittest.main()
