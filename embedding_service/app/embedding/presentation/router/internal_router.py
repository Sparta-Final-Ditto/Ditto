from uuid import UUID
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.db.database import get_db
from app.common.exception.business_exception import BusinessException
from app.common.exception.error_code import EmbeddingErrorCode
from app.common.response.api_response import ApiResponse
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.infrastructure.repository.pg_post_embedding_repository import PgPostEmbeddingRepository
from app.embedding.infrastructure.repository.pg_user_profile_repository import PgUserProfileRepository
from app.embedding.presentation.dto.embedding_dto import ProfileVectorResponse

router = APIRouter(tags=["Internal"])


def get_service(db: AsyncSession = Depends(get_db)) -> EmbeddingService:
    return EmbeddingService(
        post_repo=PgPostEmbeddingRepository(db),
        profile_repo=PgUserProfileRepository(db),
        model=ModelLoader(),
    )


@router.get(
    "/profile/{user_id}",
    summary="유저 프로필 벡터 조회",
    description="match_service OpenFeign 연동용. V_batch(EMA 프로필)와 V_today(오늘 게시글 평균)를 반환한다.",
    response_model=ApiResponse[ProfileVectorResponse],
)
async def get_profile_vector(
    user_id: UUID,
    svc: EmbeddingService = Depends(get_service),
) -> ApiResponse[ProfileVectorResponse]:
    profile, today_vector = await svc.get_profile_vector(user_id)
    if profile is None:
        raise BusinessException(EmbeddingErrorCode.PROFILE_NOT_FOUND)
    return ApiResponse.success(
        ProfileVectorResponse(
            user_id=profile.user_id,
            profile_vector=profile.vector,
            today_vector=today_vector,
            active=profile.active,
            record_count=profile.record_count,
        )
    )
