"""
UserRegisteredConsumer.handle() 단위 테스트.
Kafka·DB·모델 없이 CI에서 실행 가능.
"""
import unittest
import uuid
from datetime import date
from unittest.mock import AsyncMock, MagicMock, patch

from app.embedding.infrastructure.kafka.user_consumer import UserRegisteredConsumer

_CONSUMER_MODULE = "app.embedding.infrastructure.kafka.user_consumer"


def _user_created_msg(
    user_id: str | None = None,
    gender: str = "FEMALE",
    birthdate: str = "1999-05-20",
) -> dict:
    return {
        "eventType": "USER_CREATED",
        "userId": user_id or str(uuid.uuid4()),
        "gender": gender,
        "birthdate": birthdate,
    }


def _interests_msg(
    user_id: str | None = None,
    hashtags: list | None = None,
) -> dict:
    return {
        "eventType": "USER_INTERESTS_REGISTERED",
        "userId": user_id or str(uuid.uuid4()),
        "hashtags": hashtags if hashtags is not None else ["카페", "독서"],
    }


class TestUserRegisteredConsumer(unittest.IsolatedAsyncioTestCase):

    def setUp(self):
        self.mock_svc = AsyncMock()

        mock_db = MagicMock()
        mock_db.__aenter__ = AsyncMock(return_value=MagicMock())
        mock_db.__aexit__ = AsyncMock(return_value=False)

        p_session = patch(f"{_CONSUMER_MODULE}.AsyncSessionLocal", return_value=mock_db)
        p_post = patch(f"{_CONSUMER_MODULE}.PgPostEmbeddingRepository", return_value=AsyncMock())
        p_profile = patch(f"{_CONSUMER_MODULE}.PgUserProfileRepository", return_value=AsyncMock())
        p_model = patch(f"{_CONSUMER_MODULE}.ModelLoader", return_value=MagicMock())
        p_svc = patch(f"{_CONSUMER_MODULE}.EmbeddingService", return_value=self.mock_svc)

        for p in [p_session, p_post, p_profile, p_model, p_svc]:
            p.start()
            self.addCleanup(p.stop)

        self.consumer = UserRegisteredConsumer()

    # ── USER_CREATED ──────────────────────────────────────────────────────────

    async def test_user_created_calls_init_user_profile(self):
        """USER_CREATED → init_user_profile() 호출."""
        await self.consumer.handle(_user_created_msg())
        self.mock_svc.init_user_profile.assert_called_once()
        self.mock_svc.register_user_interests.assert_not_called()

    async def test_user_created_passes_correct_args(self):
        """USER_CREATED → user_id(UUID), gender, birthdate(date) 정확히 전달."""
        uid = str(uuid.uuid4())
        await self.consumer.handle(_user_created_msg(user_id=uid, gender="MALE", birthdate="1995-03-15"))

        args = self.mock_svc.init_user_profile.call_args.args
        self.assertEqual(str(args[0]), uid)
        self.assertEqual(args[1], "MALE")
        self.assertEqual(args[2], date(1995, 3, 15))

    async def test_user_created_invalid_uuid_ignored(self):
        """USER_CREATED — userId 형식 오류 → 예외 전파 없음."""
        msg = {"eventType": "USER_CREATED", "userId": "not-a-uuid", "gender": "F", "birthdate": "2000-01-01"}
        await self.consumer.handle(msg)
        self.mock_svc.init_user_profile.assert_not_called()

    async def test_user_created_invalid_date_ignored(self):
        """USER_CREATED — birthdate 형식 오류 → 예외 전파 없음."""
        msg = {"eventType": "USER_CREATED", "userId": str(uuid.uuid4()), "gender": "F", "birthdate": "invalid"}
        await self.consumer.handle(msg)
        self.mock_svc.init_user_profile.assert_not_called()

    async def test_user_created_missing_field_ignored(self):
        """USER_CREATED — 필수 필드 누락 → 예외 전파 없음."""
        msg = {"eventType": "USER_CREATED", "userId": str(uuid.uuid4())}
        await self.consumer.handle(msg)
        self.mock_svc.init_user_profile.assert_not_called()

    async def test_user_created_service_failure_does_not_raise(self):
        """init_user_profile 예외 → consumer 외부로 전파 없음."""
        self.mock_svc.init_user_profile.side_effect = Exception("DB 오류")
        await self.consumer.handle(_user_created_msg())  # 예외 없이 반환

    # ── USER_INTERESTS_REGISTERED ─────────────────────────────────────────────

    async def test_interests_registered_calls_register_user_interests(self):
        """USER_INTERESTS_REGISTERED → register_user_interests() 호출."""
        await self.consumer.handle(_interests_msg())
        self.mock_svc.register_user_interests.assert_called_once()
        self.mock_svc.init_user_profile.assert_not_called()

    async def test_interests_registered_passes_correct_args(self):
        """USER_INTERESTS_REGISTERED → user_id(UUID), hashtags 정확히 전달."""
        uid = str(uuid.uuid4())
        tags = ["혼공", "카페", "취준"]
        await self.consumer.handle(_interests_msg(user_id=uid, hashtags=tags))

        args = self.mock_svc.register_user_interests.call_args.args
        self.assertEqual(str(args[0]), uid)
        self.assertEqual(args[1], tags)

    async def test_interests_registered_invalid_uuid_ignored(self):
        """USER_INTERESTS_REGISTERED — userId 형식 오류 → 예외 전파 없음."""
        msg = {"eventType": "USER_INTERESTS_REGISTERED", "userId": "bad-id", "hashtags": ["태그"]}
        await self.consumer.handle(msg)
        self.mock_svc.register_user_interests.assert_not_called()

    async def test_interests_registered_missing_field_ignored(self):
        """USER_INTERESTS_REGISTERED — hashtags 누락 → 예외 전파 없음."""
        msg = {"eventType": "USER_INTERESTS_REGISTERED", "userId": str(uuid.uuid4())}
        await self.consumer.handle(msg)
        self.mock_svc.register_user_interests.assert_not_called()

    async def test_interests_registered_service_failure_does_not_raise(self):
        """register_user_interests 예외 → consumer 외부로 전파 없음."""
        self.mock_svc.register_user_interests.side_effect = Exception("임베딩 실패")
        await self.consumer.handle(_interests_msg())  # 예외 없이 반환

    # ── 기타 이벤트 ───────────────────────────────────────────────────────────

    async def test_unknown_event_type_ignored(self):
        """알 수 없는 eventType → 어떤 서비스 메서드도 호출 안 됨."""
        for event_type in ["POST_CREATED", "USER_DELETED", "UNKNOWN"]:
            self.mock_svc.reset_mock()
            await self.consumer.handle({"eventType": event_type})
            self.mock_svc.init_user_profile.assert_not_called()
            self.mock_svc.register_user_interests.assert_not_called()


if __name__ == "__main__":
    unittest.main()
