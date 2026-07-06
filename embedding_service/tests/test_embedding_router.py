"""
embedding_router 단위 테스트 — FastAPI TestClient + EmbeddingService mock.
"""
import uuid
import unittest
from datetime import datetime, timezone
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient

from app.main import app
from app.dependencies import get_embedding_service
from tests.helpers import FAKE_VECTOR, make_profile

_ROUTER_MODULE = "app.embedding.presentation.router.embedding_router"


def _make_post_embedding(status: str = "DONE") -> MagicMock:
    obj = MagicMock()
    obj.post_id = uuid.uuid4()
    obj.embedding_status = status
    obj.embedded_at = datetime.now(timezone.utc)
    return obj


class TestEmbeddingRouter(unittest.TestCase):

    def setUp(self):
        self.mock_svc = AsyncMock()

        app.dependency_overrides[get_embedding_service] = lambda: self.mock_svc
        self.client = TestClient(app, raise_server_exceptions=False)

    def tearDown(self):
        app.dependency_overrides.clear()

    # ── GET /status/{post_id} ──────────────────────────────────────────────────

    def test_get_status_200(self):
        """임베딩 상태 조회 — 정상 응답."""
        post = _make_post_embedding("DONE")
        self.mock_svc.get_embedding_status = AsyncMock(return_value=post)

        resp = self.client.get(f"/api/v1/embedding/status/{post.post_id}")

        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body["data"]["embedding_status"], "DONE")

    def test_get_status_404_when_not_found(self):
        """미존재 post_id — 404 + EMBED-001 반환."""
        self.mock_svc.get_embedding_status = AsyncMock(return_value=None)

        resp = self.client.get(f"/api/v1/embedding/status/{uuid.uuid4()}")

        self.assertEqual(resp.status_code, 404)
        self.assertEqual(resp.json()["code"], "EMBED-001")

    def test_get_status_pending(self):
        """PENDING 상태도 200으로 반환."""
        post = _make_post_embedding("PENDING")
        self.mock_svc.get_embedding_status = AsyncMock(return_value=post)

        resp = self.client.get(f"/api/v1/embedding/status/{post.post_id}")

        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["data"]["embedding_status"], "PENDING")

    # ── POST /{post_id}/retry ──────────────────────────────────────────────────

    def test_retry_202(self):
        """재처리 요청 — 202 반환."""
        self.mock_svc.retry_embedding = AsyncMock(return_value=None)

        resp = self.client.post(
            f"/api/v1/embedding/{uuid.uuid4()}/retry",
            json={"user_id": str(uuid.uuid4()), "content": "내용", "hashtags": ["태그"]},
        )

        self.assertEqual(resp.status_code, 202)

    def test_retry_calls_service(self):
        """retry — EmbeddingService.retry_embedding() 호출 검증."""
        self.mock_svc.retry_embedding = AsyncMock(return_value=None)
        post_id = uuid.uuid4()
        user_id = uuid.uuid4()

        self.client.post(
            f"/api/v1/embedding/{post_id}/retry",
            json={"user_id": str(user_id), "content": "내용", "hashtags": []},
        )

        self.mock_svc.retry_embedding.assert_called_once()

    # ── POST /api/v1/test/embedding ───────────────────────────────────────────

    def test_embed_test_returns_768_dim(self):
        """테스트 임베딩 — dimension=768, sample 길이=7."""
        self.mock_svc.embed_text = AsyncMock(return_value=FAKE_VECTOR)

        resp = self.client.post("/api/v1/test/embedding", json={"text": "테스트 문장"})

        self.assertEqual(resp.status_code, 200)
        data = resp.json()["data"]
        self.assertEqual(data["dimension"], 768)
        self.assertEqual(len(data["sample"]), 7)

    def test_embed_test_input_text_echoed(self):
        """input_text 필드에 입력값이 그대로 반환된다."""
        self.mock_svc.embed_text = AsyncMock(return_value=FAKE_VECTOR)
        text = "안녕하세요"
        resp = self.client.post("/api/v1/test/embedding", json={"text": text})
        self.assertEqual(resp.json()["data"]["input_text"], text)

    # ── POST /api/v1/test/embedding/initial-profile ───────────────────────────

    def test_embed_initial_profile_returns_768_dim(self):
        self.mock_svc.build_and_embed_initial_profile = AsyncMock(return_value=("구조화 텍스트", FAKE_VECTOR))

        resp = self.client.post(
            "/api/v1/test/embedding/initial-profile",
            json={"hashtags": ["등산", "카공"], "gender": "여", "age_group": "20대"},
        )
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["data"]["dimension"], 768)

    # ── POST /api/v1/test/embedding/post ──────────────────────────────────────

    def test_embed_post_returns_768_dim(self):
        self.mock_svc.build_and_embed_post = AsyncMock(return_value=("구조화 텍스트", FAKE_VECTOR))

        resp = self.client.post(
            "/api/v1/test/embedding/post",
            json={"content": "오늘 한강 갔다", "hashtags": ["한강"]},
        )
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["data"]["dimension"], 768)

    # ── POST /api/v1/test/embedding/embed-and-store ───────────────────────────

    def test_embed_and_store_returns_201_with_profile_state(self):
        """embed_and_store 직접 호출 — 201 + 갱신된 프로필 상태(record_count/active) 반환."""
        self.mock_svc.embed_and_store = AsyncMock(return_value=None)
        self.mock_svc.get_profile = AsyncMock(return_value=make_profile(record_count=2, active=False))
        user_id = uuid.uuid4()

        resp = self.client.post(
            "/api/v1/test/embedding/embed-and-store",
            json={"user_id": str(user_id), "content": "한강 자전거", "hashtags": ["한강"]},
        )

        self.assertEqual(resp.status_code, 201)
        data = resp.json()["data"]
        self.assertEqual(data["user_id"], str(user_id))
        self.assertEqual(data["record_count"], 2)
        self.assertFalse(data["active"])

    def test_embed_and_store_missing_profile_defaults_to_zero(self):
        """embed_and_store 후 프로필 조회 결과가 None — record_count=0, active=False 기본값."""
        self.mock_svc.embed_and_store = AsyncMock(return_value=None)
        self.mock_svc.get_profile = AsyncMock(return_value=None)

        resp = self.client.post(
            "/api/v1/test/embedding/embed-and-store",
            json={"user_id": str(uuid.uuid4()), "content": "내용", "hashtags": []},
        )

        self.assertEqual(resp.status_code, 201)
        data = resp.json()["data"]
        self.assertEqual(data["record_count"], 0)
        self.assertFalse(data["active"])

    def test_embed_and_store_calls_service_with_generated_post_id(self):
        """embed_and_store에 자동 생성된 post_id와 요청 필드가 그대로 전달된다."""
        self.mock_svc.embed_and_store = AsyncMock(return_value=None)
        self.mock_svc.get_profile = AsyncMock(return_value=None)
        user_id = uuid.uuid4()

        resp = self.client.post(
            "/api/v1/test/embedding/embed-and-store",
            json={"user_id": str(user_id), "content": "내용", "hashtags": ["태그"]},
        )

        post_id = uuid.UUID(resp.json()["data"]["post_id"])
        call_args = self.mock_svc.embed_and_store.call_args.args
        self.assertEqual(call_args[0], post_id)
        self.assertEqual(str(call_args[1]), str(user_id))
        self.assertEqual(call_args[2], "내용")
        self.assertEqual(call_args[3], ["태그"])
        self.mock_svc.get_profile.assert_called_once_with(user_id)

    # ── Health ─────────────────────────────────────────────────────────────────

    def test_health_check(self):
        resp = self.client.get("/health")
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["status"], "ok")


if __name__ == "__main__":
    unittest.main()
