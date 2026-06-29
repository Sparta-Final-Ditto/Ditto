from datetime import datetime, timezone, date
from uuid import UUID
from sqlalchemy import select, func, update, distinct
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncSession
from app.common.db.models import UserPostEmbedding
from app.embedding.domain.model.post_embedding import PostEmbedding
from app.embedding.domain.repository.post_embedding_repository import PostEmbeddingRepository


class PgPostEmbeddingRepository(PostEmbeddingRepository):

    def __init__(self, db: AsyncSession):
        self.db = db

    async def save(self, post_id: UUID, user_id: UUID, vector: list[float]) -> None:
        now = datetime.now(timezone.utc)
        stmt = (
            insert(UserPostEmbedding)
            .values(
                post_id=post_id,
                user_id=user_id,
                vector=vector,
                embedding_status="DONE",
                embedded_at=now,
            )
            .on_conflict_do_update(
                constraint="uq_user_posts_embeddings_post_id",
                set_={"vector": vector, "embedding_status": "DONE", "embedded_at": now},
            )
        )
        await self.db.execute(stmt)
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

    async def find_today_vectors(self, user_id: UUID) -> list[list[float]]:
        result = await self.db.execute(
            select(UserPostEmbedding.vector)
            .where(
                UserPostEmbedding.user_id == user_id,
                func.date(UserPostEmbedding.embedded_at) == date.today(),
                UserPostEmbedding.embedding_status == "DONE",
            )
        )
        return [row[0] for row in result.all()]

    async def find_all_done_vectors_ordered(self, user_id: UUID) -> list[list[float]]:
        result = await self.db.execute(
            select(UserPostEmbedding.vector)
            .where(
                UserPostEmbedding.user_id == user_id,
                UserPostEmbedding.embedding_status == "DONE",
            )
            .order_by(UserPostEmbedding.embedded_at.asc())
        )
        return [row[0] for row in result.all()]

    async def find_all_user_ids_with_done_embeddings(self) -> list[UUID]:
        result = await self.db.execute(
            select(distinct(UserPostEmbedding.user_id))
            .where(UserPostEmbedding.embedding_status == "DONE")
        )
        return [row[0] for row in result.all()]

    async def find_done_vectors_after(
        self, user_id: UUID, after_post_id: UUID | None
    ) -> list[tuple[UUID, list[float]]]:
        if after_post_id is None:
            stmt = (
                select(UserPostEmbedding.post_id, UserPostEmbedding.vector)
                .where(
                    UserPostEmbedding.user_id == user_id,
                    UserPostEmbedding.embedding_status == "DONE",
                    UserPostEmbedding.vector.is_not(None),
                )
                .order_by(UserPostEmbedding.embedded_at.asc())
            )
        else:
            subq = (
                select(UserPostEmbedding.id)
                .where(UserPostEmbedding.post_id == after_post_id)
                .scalar_subquery()
            )
            stmt = (
                select(UserPostEmbedding.post_id, UserPostEmbedding.vector)
                .where(
                    UserPostEmbedding.user_id == user_id,
                    UserPostEmbedding.embedding_status == "DONE",
                    UserPostEmbedding.vector.is_not(None),
                    UserPostEmbedding.id > func.coalesce(subq, 0),
                )
                .order_by(UserPostEmbedding.embedded_at.asc())
            )
        result = await self.db.execute(stmt)
        return [(row[0], row[1]) for row in result.all()]

    async def find_all_done_for_monthly_batch(
        self, user_id: UUID
    ) -> list[tuple[UUID, list[float], datetime]]:
        result = await self.db.execute(
            select(UserPostEmbedding.post_id, UserPostEmbedding.vector, UserPostEmbedding.embedded_at)
            .where(
                UserPostEmbedding.user_id == user_id,
                UserPostEmbedding.embedding_status == "DONE",
                UserPostEmbedding.vector.is_not(None),
            )
            .order_by(UserPostEmbedding.embedded_at.asc())
        )
        return [(row[0], row[1], row[2]) for row in result.all()]

    async def count_done_by_user_id(self, user_id: UUID) -> int:
        result = await self.db.execute(
            select(func.count())
            .where(
                UserPostEmbedding.user_id == user_id,
                UserPostEmbedding.embedding_status == "DONE",
            )
        )
        return result.scalar_one()

    async def reset_failed_to_done(self) -> int:
        result = await self.db.execute(
            update(UserPostEmbedding)
            .where(UserPostEmbedding.embedding_status == "FAILED")
            .values(embedding_status="DONE")
        )
        await self.db.commit()
        return result.rowcount  # type: ignore[return-value]

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
