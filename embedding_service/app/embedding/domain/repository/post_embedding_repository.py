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

    @abstractmethod
    async def find_all_done_vectors_ordered(self, user_id: UUID) -> list[list[float]]:
        """배치용: 해당 유저의 DONE 상태 벡터를 embedded_at ASC 순서로 반환."""
        ...

    @abstractmethod
    async def find_all_user_ids_with_done_embeddings(self) -> list[UUID]:
        """배치용: DONE 임베딩이 하나 이상 존재하는 유저 ID 목록."""
        ...

    @abstractmethod
    async def reset_failed_to_done(self) -> int:
        """배치 전처리: FAILED 레코드를 DONE으로 되돌려 EMA 재계산 대상에 포함."""
        ...
