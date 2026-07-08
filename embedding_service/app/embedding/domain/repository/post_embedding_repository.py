from abc import ABC, abstractmethod
from datetime import datetime
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

    @abstractmethod
    async def find_done_vectors_after(
        self, user_id: UUID, after_post_id: UUID | None
    ) -> list[tuple[UUID, list[float]]]:
        """배치 증분용: after_post_id 이후에 추가된 DONE (post_id, vector) 목록 반환.
        after_post_id=None 이면 전체 반환."""
        ...

    @abstractmethod
    async def find_all_done_for_monthly_batch(
        self, user_id: UUID
    ) -> list[tuple[UUID, list[float], datetime]]:
        """월배치용: DONE 상태의 (post_id, vector, embedded_at) 목록을 embedded_at ASC 순서로 반환."""
        ...

    @abstractmethod
    async def delete_by_post_id(self, post_id: UUID) -> None:
        """hard delete: 임베딩 레코드 물리 삭제."""
        ...

    @abstractmethod
    async def count_done_by_user_id(self, user_id: UUID) -> int:
        """해당 유저의 DONE 상태 임베딩 개수를 반환."""
        ...
