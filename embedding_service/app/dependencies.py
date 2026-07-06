from fastapi import Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.db.database import get_db
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.infrastructure.service_factory import build_embedding_service


def get_embedding_service(db: AsyncSession = Depends(get_db)) -> EmbeddingService:
    return build_embedding_service(db)
