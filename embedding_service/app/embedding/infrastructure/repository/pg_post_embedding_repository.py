from uuid import UUID
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.common.db.models import UserPostEmbedding
from app.embedding.domain.model.post_embedding import PostEmbedding
from app.embedding.domain.repository.post_embedding_repository import PostEmbeddingRepository


class PgPostEmbeddingRepository(PostEmbeddingRepository):

    def __init__(self, db: AsyncSession):
        self.db = db

    async def save(self, post_id: UUID, user_id: UUID, vector: list[float]) -> None:
        self.db.add(UserPostEmbedding(
            post_id=post_id,
            user_id=user_id,
            vector=vector,
            embedding_status="DONE",
        ))
        await self.db.commit()

    async def find_by_post_id(self, post_id: UUID) -> PostEmbedding | None:
        result = await self.db.execute(
            select(UserPostEmbedding).where(UserPostEmbedding.post_id == post_id)
        )
        row = result.scalar_one_or_none()
        return self._to_domain(row) if row else None

    async def find_all_by_user_id(self, user_id: UUID) -> list[PostEmbedding]:
        result = await self.db.execute(
            select(UserPostEmbedding)
            .where(UserPostEmbedding.user_id == user_id)
            .order_by(UserPostEmbedding.created_at.asc())
        )
        return [self._to_domain(row) for row in result.scalars().all()]

    async def update_status(self, post_id: UUID, status: str) -> None:
        result = await self.db.execute(
            select(UserPostEmbedding).where(UserPostEmbedding.post_id == post_id)
        )
        row = result.scalar_one_or_none()
        if row:
            row.embedding_status = status
            await self.db.commit()

    @staticmethod
    def _to_domain(row: UserPostEmbedding) -> PostEmbedding:
        return PostEmbedding(
            post_id=row.post_id,
            user_id=row.user_id,
            vector=row.vector,
            embedding_status=row.embedding_status,
            embedded_at=row.embedded_at,
            created_at=row.created_at,
        )
