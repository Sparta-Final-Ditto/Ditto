import numpy as np
from uuid import UUID
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.db.database import get_db
from app.common.exception.business_exception import BusinessException
from app.common.exception.error_code import EmbeddingErrorCode
from app.common.response.api_response import ApiResponse
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.domain.algorithm.profile_builder import build_initial_text
from app.embedding.domain.algorithm.post_text_builder import build_post_text
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.infrastructure.repository.pg_post_embedding_repository import PgPostEmbeddingRepository
from app.embedding.infrastructure.repository.pg_user_profile_repository import PgUserProfileRepository
from app.embedding.presentation.dto.embedding_dto import (
    EmbeddingStatusResponse,
    RetryRequest,
)

router = APIRouter(tags=["Embedding"])


def get_service(db: AsyncSession = Depends(get_db)) -> EmbeddingService:
    return EmbeddingService(
        post_repo=PgPostEmbeddingRepository(db),
        profile_repo=PgUserProfileRepository(db),
        model=ModelLoader(),
    )


# ── 실제 엔드포인트 ────────────────────────────────────────

@router.get(
    "/status/{post_id}",
    summary="임베딩 처리 상태 조회",
    description="게시글의 임베딩 처리 상태(DONE/PENDING/FAILED)를 조회한다.",
    response_model=ApiResponse[EmbeddingStatusResponse],
)
async def get_status(
    post_id: UUID,
    svc: EmbeddingService = Depends(get_service),
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
    svc: EmbeddingService = Depends(get_service),
) -> ApiResponse[None]:
    await svc.retry_embedding(post_id, body.user_id, body.content, body.hashtags)
    return ApiResponse.accepted()


# ── 테스트 엔드포인트 ──────────────────────────────────────

class EmbedTestResponse(BaseModel):
    input_text: str
    dimension: int
    sample: list[float]


class RawTextRequest(BaseModel):
    text: str


@router.post(
    "/test",
    summary="[테스트] 텍스트 직접 입력",
    description="텍스트를 직접 입력하여 임베딩 벡터 차원과 샘플을 반환한다.",
    response_model=ApiResponse[EmbedTestResponse],
)
async def embed_test(body: RawTextRequest) -> ApiResponse[EmbedTestResponse]:
    model = ModelLoader.get_model()
    vector = np.array(model.encode(body.text))
    return ApiResponse.success(
        EmbedTestResponse(input_text=body.text, dimension=len(vector), sample=vector[:7].tolist())
    )


class InitialProfileRequest(BaseModel):
    hashtags: list[str]
    gender: str
    age_group: str


@router.post(
    "/test/initial-profile",
    summary="[테스트] 초기 프로필 임베딩",
    description="회원가입 정보(해시태그, 성별, 나이대)로 구조화 텍스트를 생성하고 임베딩 결과를 반환한다.",
    response_model=ApiResponse[EmbedTestResponse],
)
async def embed_initial_profile(body: InitialProfileRequest) -> ApiResponse[EmbedTestResponse]:
    text = build_initial_text(body.hashtags, body.gender, body.age_group)
    model = ModelLoader.get_model()
    vector = np.array(model.encode(text))
    return ApiResponse.success(
        EmbedTestResponse(input_text=text, dimension=len(vector), sample=vector[:7].tolist())
    )


class PostEmbedRequest(BaseModel):
    content: str
    hashtags: list[str]


@router.post(
    "/test/post",
    summary="[테스트] 게시글 임베딩",
    description="게시글 정보(본문, 태그)로 구조화 텍스트를 생성하고 임베딩 결과를 반환한다.",
    response_model=ApiResponse[EmbedTestResponse],
)
async def embed_post(body: PostEmbedRequest) -> ApiResponse[EmbedTestResponse]:
    text = build_post_text(body.content, body.hashtags)
    model = ModelLoader.get_model()
    vector = np.array(model.encode(text))
    return ApiResponse.success(
        EmbedTestResponse(input_text=text, dimension=len(vector), sample=vector[:7].tolist())
    )


class EmbedAndStoreRequest(BaseModel):
    user_id: UUID
    content: str
    hashtags: list[str]


class EmbedAndStoreResponse(BaseModel):
    post_id: UUID
    user_id: UUID
    record_count: int
    active: bool


@router.post(
    "/test/embed-and-store",
    summary="[테스트] embed_and_store 직접 호출",
    description="게시글 저장 + 프로필 record_count/active 갱신 로직을 직접 실행한다. "
                "post_id는 자동 생성되며, 결과 프로필 상태를 반환한다.",
    response_model=ApiResponse[EmbedAndStoreResponse],
    status_code=201,
)
async def embed_and_store_test(
    body: EmbedAndStoreRequest,
    svc: EmbeddingService = Depends(get_service),
) -> ApiResponse[EmbedAndStoreResponse]:
    import uuid as _uuid
    post_id = _uuid.uuid4()
    await svc.embed_and_store(post_id, body.user_id, body.content, body.hashtags)
    profile = await svc.profile_repo.find_by_user_id(body.user_id)
    return ApiResponse.success(
        EmbedAndStoreResponse(
            post_id=post_id,
            user_id=body.user_id,
            record_count=profile.record_count if profile else 0,
            active=profile.active if profile else False,
        )
    )
