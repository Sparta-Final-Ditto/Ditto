from abc import ABC, abstractmethod
from uuid import UUID
from app.embedding.domain.model.post_embedding import PostEmbedding


class PostEmbeddingRepository(ABC):

    @abstractmethod
    async def save(self, post_id: UUID, user_id: UUID, vector: list[float]) -> None: ...

    @abstractmethod
    async def find_by_post_id(self, post_id: UUID) -> PostEmbedding | None: ...

    @abstractmethod
    async def find_all_by_user_id(self, user_id: UUID) -> list[PostEmbedding]: ...

    @abstractmethod
    async def update_status(self, post_id: UUID, status: str) -> None: ...

    @abstractmethod
    async def find_today_vectors(self, user_id: UUID) -> list[list[float]]: ...
