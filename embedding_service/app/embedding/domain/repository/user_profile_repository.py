from abc import ABC, abstractmethod
from uuid import UUID
from app.common.db.models import UserProfileEmbedding


class UserProfileRepository(ABC):

    @abstractmethod
    async def find_by_user_id(self, user_id: UUID) -> UserProfileEmbedding | None: ...

    @abstractmethod
    async def upsert(
        self,
        user_id: UUID,
        vector: list[float],
        record_count: int,
        active: bool,
        last_processed_record_id: UUID | None = None,
    ) -> None: ...
