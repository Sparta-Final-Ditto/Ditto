from uuid import UUID
from fastapi import APIRouter, Depends

from app.common.exception.business_exception import BusinessException
from app.common.exception.error_code import EmbeddingErrorCode
from app.common.response.api_response import ApiResponse
from app.dependencies import get_embedding_service
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.presentation.dto.embedding_dto import (
    EmbeddingStatusResponse,
    RetryRequest,
)

router = APIRouter(tags=["Embedding"])


@router.get(
    "/status/{post_id}",
    summary="임베딩 처리 상태 조회",
    description="게시글의 임베딩 처리 상태(DONE/PENDING/FAILED)를 조회한다.",
    response_model=ApiResponse[EmbeddingStatusResponse],
)
async def get_status(
    post_id: UUID,
    svc: EmbeddingService = Depends(get_embedding_service),
) -> ApiResponse[EmbeddingStatusResponse]:
    result = await svc.get_embedding_status(post_id)
    if result is None:
        raise BusinessException(EmbeddingErrorCode.EMBEDDING_NOT_FOUND)
    return ApiResponse.success(
        EmbeddingStatusResponse(
            post_id=result.post_id,
            embedding_status=result.embedding_status,
            embedded_at=result.embedded_at,
        )
    )


@router.post(
    "/{post_id}/retry",
    summary="임베딩 재처리",
    description="임베딩 실패 게시글을 원본 데이터로 재처리한다.",
    response_model=ApiResponse[None],
    status_code=202,
)
async def retry_embedding(
    post_id: UUID,
    body: RetryRequest,
    svc: EmbeddingService = Depends(get_embedding_service),
) -> ApiResponse[None]:
    await svc.retry_embedding(post_id, body.user_id, body.content, body.hashtags)
    return ApiResponse.accepted()
