from datetime import date
from uuid import UUID
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.common.db.models import UserProfileEmbedding
from app.embedding.domain.model.user_profile import UserProfile
from app.embedding.domain.repository.user_profile_repository import UserProfileRepository


class PgUserProfileRepository(UserProfileRepository):

    def __init__(self, db: AsyncSession):
        self.db = db

    async def find_by_user_id(self, user_id: UUID) -> UserProfile | None:
        row = await self.db.get(UserProfileEmbedding, user_id)
        if row is None:
            return None
        return self._to_domain(row)

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

    async def init_user_profile(
        self,
        user_id: UUID,
        gender: str,
        birthdate: date,
    ) -> None:
        row = await self.db.get(UserProfileEmbedding, user_id)
        if row:
            row.gender = gender
            row.birthdate = birthdate
        else:
            self.db.add(UserProfileEmbedding(
                user_id=user_id,
                vector=None,
                record_count=0,
                active=False,
                gender=gender,
                birthdate=birthdate,
            ))
        await self.db.commit()

    async def update_initial_vector(self, user_id: UUID, vector: list[float]) -> None:
        row = await self.db.get(UserProfileEmbedding, user_id)
        if row is None:
            return
        row.vector = vector
        await self.db.commit()

    async def find_all_user_ids(self) -> list[UUID]:
        result = await self.db.execute(select(UserProfileEmbedding.user_id))
        return [row[0] for row in result.all()]

    async def find_active_user_ids(self) -> list[UUID]:
        result = await self.db.execute(
            select(UserProfileEmbedding.user_id)
            .where(
                UserProfileEmbedding.active.is_(True),
                UserProfileEmbedding.deleted_at.is_(None),
            )
        )
        return [row[0] for row in result.all()]

    @staticmethod
    def _to_domain(row: UserProfileEmbedding) -> UserProfile:
        return UserProfile(
            user_id=row.user_id,
            vector=row.vector,
            record_count=row.record_count,
            active=row.active,
            last_processed_record_id=row.last_processed_record_id,
            gender=row.gender,
            birthdate=row.birthdate,
            updated_at=row.updated_at,
            created_at=row.created_at,
        )
