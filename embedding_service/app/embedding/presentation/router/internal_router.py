from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException

from app.common.exception.business_exception import BusinessException
from app.common.exception.error_code import EmbeddingErrorCode
from app.common.response.api_response import ApiResponse
from app.dependencies import get_embedding_service
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.presentation.dto.embedding_dto import (
    ActiveUserIdsResponse,
    ProfileBatchItem,
    ProfileBatchRequest,
    ProfileBatchResponse,
    ProfileVectorResponse,
)

router = APIRouter(tags=["Internal"])


@router.get(
    "/profiles/active/ids",
    summary="매칭 가능 유저 ID 목록 조회",
    description="match_service OpenFeign : active=True인 유저 ID 목록을 반환한다.",
    response_model=ApiResponse[ActiveUserIdsResponse],
)
async def get_active_user_ids(
    svc: EmbeddingService = Depends(get_embedding_service),
) -> ApiResponse[ActiveUserIdsResponse]:
    user_ids = await svc.profile_repo.find_active_user_ids()
    return ApiResponse.success(
        ActiveUserIdsResponse(user_ids=user_ids, count=len(user_ids))
    )


@router.post(
    "/profiles/batch",
    summary="유저 프로필 벡터 일괄 조회",
    description="match_service OpenFeign : user_id 리스트(최대 100개)로 profile_vector + today_vector를 일괄 반환한다.",
    response_model=ApiResponse[ProfileBatchResponse],
)
async def get_profiles_batch(
    body: ProfileBatchRequest,
    svc: EmbeddingService = Depends(get_embedding_service),
) -> ApiResponse[ProfileBatchResponse]:
    if len(body.user_ids) > 100:
        raise HTTPException(status_code=400, detail="user_ids는 최대 100개까지 허용됩니다.")

    results = await svc.get_profiles_batch(body.user_ids)
    profiles = [
        ProfileBatchItem(
            user_id=profile.user_id,
            profile_vector=profile.vector,
            today_vector=today_vector,
        )
        for profile, today_vector in results
    ]
    return ApiResponse.success(ProfileBatchResponse(profiles=profiles))


@router.get(
    "/profile/{user_id}",
    summary="유저 프로필 벡터 조회",
    description="match_service OpenFeign : V_batch(EMA 프로필)와 V_today(오늘 게시글 평균)를 반환한다.",
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
