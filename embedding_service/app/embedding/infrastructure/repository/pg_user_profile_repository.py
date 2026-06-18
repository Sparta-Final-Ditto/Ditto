from uuid import UUID
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.common.db.models import UserProfileEmbedding
from app.embedding.domain.repository.user_profile_repository import UserProfileRepository


class PgUserProfileRepository(UserProfileRepository):

    def __init__(self, db: AsyncSession):
        self.db = db

    async def find_by_user_id(self, user_id: UUID) -> UserProfileEmbedding | None:
        return await self.db.get(UserProfileEmbedding, user_id)

    async def upsert(
        self,
        user_id: UUID,
        vector: list[float],
        record_count: int,
        active: bool,
        last_processed_record_id: UUID | None = None,
    ) -> None:
        row = await self.db.get(UserProfileEmbedding, user_id)
        if row:
            row.vector = vector
            row.record_count = record_count
            row.active = active
            row.last_processed_record_id = last_processed_record_id
        else:
            self.db.add(UserProfileEmbedding(
                user_id=user_id,
                vector=vector,
                record_count=record_count,
                active=active,
                last_processed_record_id=last_processed_record_id,
            ))
        await self.db.commit()
