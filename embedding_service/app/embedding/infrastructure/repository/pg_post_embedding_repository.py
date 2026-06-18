from uuid import UUID
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.common.db.models import UserPostEmbedding
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

    async def find_by_post_id(self, post_id: UUID) -> UserPostEmbedding | None:
        result = await self.db.execute(
            select(UserPostEmbedding).where(UserPostEmbedding.post_id == post_id)
        )
        return result.scalar_one_or_none()

    async def find_all_by_user_id(self, user_id: UUID) -> list[UserPostEmbedding]:
        result = await self.db.execute(
            select(UserPostEmbedding)
            .where(UserPostEmbedding.user_id == user_id)
            .order_by(UserPostEmbedding.created_at.asc())
        )
        return list(result.scalars().all())

    async def update_status(self, post_id: UUID, status: str) -> None:
        row = await self.find_by_post_id(post_id)
        if row:
            row.embedding_status = status
            await self.db.commit()
