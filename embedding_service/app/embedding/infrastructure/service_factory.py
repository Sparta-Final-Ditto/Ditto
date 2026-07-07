from sqlalchemy.ext.asyncio import AsyncSession

from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.infrastructure.batch.batch_embedding import BatchEmbeddingRunner
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.infrastructure.repository.pg_post_embedding_repository import PgPostEmbeddingRepository
from app.embedding.infrastructure.repository.pg_user_profile_repository import PgUserProfileRepository


def build_embedding_service(db: AsyncSession) -> EmbeddingService:
    return EmbeddingService(
        post_repo=PgPostEmbeddingRepository(db),
        profile_repo=PgUserProfileRepository(db),
        model=ModelLoader(),
        batch_runner=BatchEmbeddingRunner(),
    )
