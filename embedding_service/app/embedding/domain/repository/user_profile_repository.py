from abc import ABC, abstractmethod
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
    async def find_all_user_ids(self) -> list[UUID]:
        """배치용: 프로필이 존재하는 모든 유저 ID 목록."""
        ...

    @abstractmethod
    async def find_active_user_ids(self) -> list[UUID]:
        """match_service 배치용: active=True인 유저 ID 목록."""
        ...
