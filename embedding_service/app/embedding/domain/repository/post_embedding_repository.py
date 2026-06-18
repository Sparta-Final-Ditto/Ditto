from abc import ABC, abstractmethod
from uuid import UUID
from app.common.db.models import UserPostEmbedding


class PostEmbeddingRepository(ABC):

    @abstractmethod
    async def save(self, post_id: UUID, user_id: UUID, vector: list[float]) -> None: ...

    @abstractmethod
    async def find_by_post_id(self, post_id: UUID) -> UserPostEmbedding | None: ...

    @abstractmethod
    async def find_all_by_user_id(self, user_id: UUID) -> list[UserPostEmbedding]: ...

    @abstractmethod
    async def update_status(self, post_id: UUID, status: str) -> None: ...
