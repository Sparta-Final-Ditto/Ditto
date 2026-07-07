"""
internal_router 단위 테스트 — FastAPI TestClient + EmbeddingService mock.
"""
import uuid
import unittest
from unittest.mock import AsyncMock, MagicMock

from fastapi.testclient import TestClient

from app.main import app
from app.dependencies import get_embedding_service
from tests.helpers import FakeModel, make_profile, FAKE_VECTOR


def _make_profile_tuple(active: bool = True, record_count: int = 5):
    profile = make_profile(record_count=record_count, active=active, vector=FAKE_VECTOR)
    profile.user_id = uuid.uuid4()
    return profile, FAKE_VECTOR  # (profile, today_vector)


class TestInternalRouter(unittest.TestCase):

    def setUp(self):
        self.mock_svc = AsyncMock()
        app.dependency_overrides[get_embedding_service] = lambda: self.mock_svc
        self.client = TestClient(app, raise_server_exceptions=False)

    def tearDown(self):
        app.dependency_overrides.clear()

    # ── GET /api/v1/internal/embedding/profiles/active/ids ────────────────────────────

    def test_get_active_ids_200(self):
        """활성 유저 ID 목록 — 200 + count 반환."""
        user_ids = [uuid.uuid4(), uuid.uuid4()]
        self.mock_svc.get_active_user_ids = AsyncMock(return_value=user_ids)

        resp = self.client.get("/api/v1/internal/embedding/profiles/active/ids")

        self.assertEqual(resp.status_code, 200)
        body = resp.json()["data"]
        self.assertEqual(body["count"], 2)
        self.assertEqual(len(body["user_ids"]), 2)

    def test_get_active_ids_empty(self):
        """활성 유저 없을 때 — count=0."""
        self.mock_svc.get_active_user_ids = AsyncMock(return_value=[])

        resp = self.client.get("/api/v1/internal/embedding/profiles/active/ids")

        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["data"]["count"], 0)

    # ── POST /api/v1/internal/embedding/profiles/batch ────────────────────────────────

    def test_get_profiles_batch_200(self):
        """유저 프로필 일괄 조회 — 200 + profiles 목록 반환."""
        profile, today = _make_profile_tuple()
        self.mock_svc.get_profiles_batch = AsyncMock(return_value=[(profile, today)])

        resp = self.client.post(
            "/api/v1/internal/embedding/profiles/batch",
            json={"user_ids": [str(uuid.uuid4())]},
        )

        self.assertEqual(resp.status_code, 200)
        self.assertEqual(len(resp.json()["data"]["profiles"]), 1)

    def test_get_profiles_batch_over_100_returns_400(self):
        """user_ids 101개 — 400 에러 반환."""
        user_ids = [str(uuid.uuid4()) for _ in range(101)]

        resp = self.client.post(
            "/api/v1/internal/embedding/profiles/batch",
            json={"user_ids": user_ids},
        )

        self.assertEqual(resp.status_code, 400)

    def test_get_profiles_batch_exactly_100_allowed(self):
        """user_ids 정확히 100개 — 정상 처리."""
        self.mock_svc.get_profiles_batch = AsyncMock(return_value=[])
        user_ids = [str(uuid.uuid4()) for _ in range(100)]

        resp = self.client.post(
            "/api/v1/internal/embedding/profiles/batch",
            json={"user_ids": user_ids},
        )

        self.assertEqual(resp.status_code, 200)

    # ── GET /api/v1/internal/embedding/profile/{user_id} ──────────────────────────────

    def test_get_profile_vector_200(self):
        """단일 유저 프로필 조회 — 200 + profile_vector 반환."""
        profile, today = _make_profile_tuple(active=True, record_count=5)
        user_id = profile.user_id
        self.mock_svc.get_profile_vector = AsyncMock(return_value=(profile, today))

        resp = self.client.get(f"/api/v1/internal/embedding/profile/{user_id}")

        self.assertEqual(resp.status_code, 200)
        data = resp.json()["data"]
        self.assertTrue(data["active"])
        self.assertEqual(data["record_count"], 5)
        self.assertEqual(len(data["profile_vector"]), 768)

    def test_get_profile_vector_404_when_not_found(self):
        """미존재 유저 — 404 + EMBED-004 반환."""
        self.mock_svc.get_profile_vector = AsyncMock(return_value=(None, None))

        resp = self.client.get(f"/api/v1/internal/embedding/profile/{uuid.uuid4()}")

        self.assertEqual(resp.status_code, 404)
        self.assertEqual(resp.json()["code"], "EMBED-004")

    def test_get_profile_vector_today_vector_none(self):
        """오늘 게시글 없는 유저 — today_vector=null."""
        profile, _ = _make_profile_tuple()
        self.mock_svc.get_profile_vector = AsyncMock(return_value=(profile, None))

        resp = self.client.get(f"/api/v1/internal/embedding/profile/{profile.user_id}")

        self.assertEqual(resp.status_code, 200)
        self.assertIsNone(resp.json()["data"]["today_vector"])

    # ── POST /api/v1/internal/embedding/embed-text ─────────────────────────────

    def test_embed_text_200(self):
        """임의 텍스트 임베딩 — 200 + vector/dimension 반환."""
        self.mock_svc.embed_text = AsyncMock(return_value=FAKE_VECTOR)

        resp = self.client.post(
            "/api/v1/internal/embedding/embed-text",
            json={"text": "매칭 서비스 RAG 텍스트"},
        )

        self.assertEqual(resp.status_code, 200)
        data = resp.json()["data"]
        self.assertEqual(data["dimension"], 768)
        self.assertEqual(len(data["vector"]), 768)

    def test_embed_text_calls_service_with_input_text(self):
        """embed_text가 요청 본문의 text를 그대로 전달받는다."""
        self.mock_svc.embed_text = AsyncMock(return_value=FAKE_VECTOR)
        text = "임베딩할 문장"

        self.client.post("/api/v1/internal/embedding/embed-text", json={"text": text})

        self.mock_svc.embed_text.assert_called_once_with(text)


if __name__ == "__main__":
    unittest.main()
