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
