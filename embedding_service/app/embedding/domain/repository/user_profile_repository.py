from abc import ABC, abstractmethod
from datetime import date
from uuid import UUID
from app.embedding.domain.model.user_profile import UserProfile


class UserProfileRepository(ABC):

    @abstractmethod
    async def find_by_user_id(self, user_id: UUID) -> UserProfile | None: ...

    @abstractmethod
    async def upsert(
        self,
        user_id: UUID,
        vector: list[float],
        record_count: int,
        active: bool,
        last_processed_record_id: UUID | None = None,
    ) -> None: ...

    @abstractmethod
    async def init_user_profile(
        self,
        user_id: UUID,
        gender: str,
        birthdate: date,
    ) -> None:
        """USER_CREATED: vector=NULL stub 행 생성 (이미 존재하면 메타데이터만 갱신)."""
        ...

    @abstractmethod
    async def update_initial_vector(self, user_id: UUID, vector: list[float]) -> None:
        """USER_INTERESTS_REGISTERED: vector만 갱신."""
        ...

    @abstractmethod
    async def find_all_user_ids(self) -> list[UUID]:
        """배치용: 프로필이 존재하는 모든 유저 ID 목록."""
        ...

    @abstractmethod
    async def find_active_user_ids(self) -> list[UUID]:
        """match_service 배치용: active=True인 유저 ID 목록."""
        ...

    @abstractmethod
    async def sync_count_and_active(self, user_id: UUID, done_count: int) -> None:
        """record_count/active를 done_count 기준으로 동기화."""
        ...
