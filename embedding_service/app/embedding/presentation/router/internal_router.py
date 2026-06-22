from uuid import UUID
from fastapi import APIRouter, Depends

from app.common.exception.business_exception import BusinessException
from app.common.exception.error_code import EmbeddingErrorCode
from app.common.response.api_response import ApiResponse
from app.dependencies import get_embedding_service
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.presentation.dto.embedding_dto import ActiveUserIdsResponse, ProfileVectorResponse

router = APIRouter(tags=["Internal"])


@router.get(
    "/profiles/active/ids",
    summary="매칭 가능 유저 ID 목록 조회",
    description="active=True인 유저 ID 목록을 반환한다. match_service Spring Batch 파티셔닝용.",
    response_model=ApiResponse[ActiveUserIdsResponse],
)
async def get_active_user_ids(
    svc: EmbeddingService = Depends(get_embedding_service),
) -> ApiResponse[ActiveUserIdsResponse]:
    user_ids = await svc.profile_repo.find_active_user_ids()
    return ApiResponse.success(
        ActiveUserIdsResponse(user_ids=user_ids, count=len(user_ids))
    )


@router.get(
    "/profile/{user_id}",
    summary="유저 프로필 벡터 조회",
    description="match_service OpenFeign 연동용. V_batch(EMA 프로필)와 V_today(오늘 게시글 평균)를 반환한다.",
    response_model=ApiResponse[ProfileVectorResponse],
)
async def get_profile_vector(
    user_id: UUID,
    svc: EmbeddingService = Depends(get_embedding_service),
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
