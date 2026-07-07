"""
ApiResponse, ErrorCode, BusinessException, exception_handler 단위 테스트.
"""
import unittest
from unittest.mock import AsyncMock, MagicMock

from app.common.response.api_response import ApiResponse
from app.common.exception.error_code import CommonErrorCode, EmbeddingErrorCode
from app.common.exception.business_exception import BusinessException
from app.common.exception.exception_handler import (
    business_exception_handler,
    validation_exception_handler,
    unhandled_exception_handler,
)


# ── ApiResponse ────────────────────────────────────────────────────────────────

class TestApiResponse(unittest.TestCase):

    def test_success_status_and_message(self):
        resp = ApiResponse.success({"key": "value"})
        self.assertEqual(resp.status, 200)
        self.assertEqual(resp.message, "SUCCESS")
        self.assertEqual(resp.data, {"key": "value"})

    def test_success_no_content(self):
        resp = ApiResponse.success_no_content()
        self.assertEqual(resp.status, 200)
        self.assertIsNone(resp.data)

    def test_created(self):
        resp = ApiResponse.created({"id": 1})
        self.assertEqual(resp.status, 201)
        self.assertEqual(resp.message, "CREATED")

    def test_accepted(self):
        resp = ApiResponse.accepted()
        self.assertEqual(resp.status, 202)
        self.assertIsNone(resp.data)

    def test_deleted(self):
        resp = ApiResponse.deleted()
        self.assertEqual(resp.status, 200)
        self.assertEqual(resp.message, "DELETED")
        self.assertIsNone(resp.data)

    def test_updated(self):
        resp = ApiResponse.updated({"updated": True})
        self.assertEqual(resp.status, 200)
        self.assertEqual(resp.message, "UPDATED")

    def test_error_with_errors_list(self):
        resp = ApiResponse.error(400, "COMMON-001", "잘못된 입력", ["field: 필수값"])
        self.assertEqual(resp.status, 400)
        self.assertEqual(resp.code, "COMMON-001")
        self.assertIn("field: 필수값", resp.errors)

    def test_error_without_errors_list(self):
        resp = ApiResponse.error(500, "COMMON-005", "서버 오류")
        self.assertIsNone(resp.errors)


# ── ErrorCode ──────────────────────────────────────────────────────────────────

class TestErrorCode(unittest.TestCase):

    def test_common_error_code_properties(self):
        ec = CommonErrorCode.INVALID_INPUT
        self.assertEqual(ec.code, "COMMON-001")
        self.assertEqual(ec.status, 400)
        self.assertIsInstance(ec.message, str)

    def test_internal_server_error(self):
        ec = CommonErrorCode.INTERNAL_SERVER_ERROR
        self.assertEqual(ec.status, 500)

    def test_embedding_not_found(self):
        ec = EmbeddingErrorCode.EMBEDDING_NOT_FOUND
        self.assertEqual(ec.code, "EMBED-001")
        self.assertEqual(ec.status, 404)

    def test_profile_not_found(self):
        ec = EmbeddingErrorCode.PROFILE_NOT_FOUND
        self.assertEqual(ec.code, "EMBED-004")
        self.assertEqual(ec.status, 404)

    def test_all_embedding_error_codes_have_embed_prefix(self):
        for ec in EmbeddingErrorCode:
            self.assertTrue(ec.code.startswith("EMBED-"), f"{ec.name} 코드가 EMBED- 접두사 없음")

    def test_all_common_error_codes_have_common_prefix(self):
        for ec in CommonErrorCode:
            self.assertTrue(ec.code.startswith("COMMON-"), f"{ec.name} 코드가 COMMON- 접두사 없음")


# ── BusinessException ──────────────────────────────────────────────────────────

class TestBusinessException(unittest.TestCase):

    def test_error_code_stored(self):
        exc = BusinessException(EmbeddingErrorCode.PROFILE_NOT_FOUND)
        self.assertEqual(exc.error_code, EmbeddingErrorCode.PROFILE_NOT_FOUND)

    def test_message_matches_error_code(self):
        ec = EmbeddingErrorCode.EMBEDDING_NOT_FOUND
        exc = BusinessException(ec)
        self.assertEqual(str(exc), ec.message)

    def test_is_exception(self):
        exc = BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR)
        self.assertIsInstance(exc, Exception)


# ── Exception Handlers ─────────────────────────────────────────────────────────

class TestExceptionHandlers(unittest.IsolatedAsyncioTestCase):

    def _mock_request(self):
        return MagicMock()

    async def test_business_exception_handler_status(self):
        exc = BusinessException(EmbeddingErrorCode.PROFILE_NOT_FOUND)
        response = await business_exception_handler(self._mock_request(), exc)
        self.assertEqual(response.status_code, 404)

    async def test_business_exception_handler_body_code(self):
        exc = BusinessException(EmbeddingErrorCode.EMBEDDING_NOT_FOUND)
        response = await business_exception_handler(self._mock_request(), exc)
        import json
        body = json.loads(response.body)
        self.assertEqual(body["code"], "EMBED-001")

    async def test_unhandled_exception_handler_returns_500(self):
        response = await unhandled_exception_handler(self._mock_request(), Exception("예상 못한 오류"))
        self.assertEqual(response.status_code, 500)


if __name__ == "__main__":
    unittest.main()
