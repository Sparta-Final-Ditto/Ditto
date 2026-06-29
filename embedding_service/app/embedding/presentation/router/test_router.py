import asyncio
import uuid as _uuid
import numpy as np
from uuid import UUID
from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.common.response.api_response import ApiResponse
from app.dependencies import get_embedding_service, get_model
from app.embedding.application.port.embedding_model_port import EmbeddingModelPort
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.domain.algorithm.post_text_builder import build_post_text
from app.embedding.domain.algorithm.profile_builder import build_initial_text
from app.embedding.infrastructure.batch.batch_embedding import run_batch, run_monthly_batch

router = APIRouter(tags=["Test"])


# ── 배치 트리거 ────────────────────────────────────────────

@router.post(
    "/batch/daily",
    summary="일배치 수동 트리거",
    description="EMA 일배치를 즉시 실행한다. 프로덕션에서는 매일 KST 03:00 자동 실행.",
    response_model=ApiResponse[str],
)
async def trigger_daily_batch() -> ApiResponse[str]:
    asyncio.create_task(run_batch())
    return ApiResponse.success("일배치 실행 시작")


@router.post(
    "/batch/monthly",
    summary="월배치 수동 트리거",
    description="시간 감쇠 가중 평균 월배치를 즉시 실행한다. 프로덕션에서는 매월 1일 KST 04:00 자동 실행.",
    response_model=ApiResponse[str],
)
async def trigger_monthly_batch() -> ApiResponse[str]:
    asyncio.create_task(run_monthly_batch())
    return ApiResponse.success("월배치 실행 시작")


# ── 임베딩 검증 ────────────────────────────────────────────

class EmbedTestResponse(BaseModel):
    input_text: str
    dimension: int
    sample: list[float]


class RawTextRequest(BaseModel):
    text: str


@router.post(
    "/embedding",
    summary="텍스트 직접 임베딩",
    description="텍스트를 직접 입력하여 임베딩 벡터 차원과 샘플을 반환한다.",
    response_model=ApiResponse[EmbedTestResponse],
)
async def embed_test(
    body: RawTextRequest,
    model: EmbeddingModelPort = Depends(get_model),
) -> ApiResponse[EmbedTestResponse]:
    vector = np.array(model.encode(body.text))
    return ApiResponse.success(
        EmbedTestResponse(input_text=body.text, dimension=len(vector), sample=vector[:7].tolist())
    )


class InitialProfileRequest(BaseModel):
    hashtags: list[str]
    gender: str
    age_group: str


@router.post(
    "/embedding/initial-profile",
    summary="초기 프로필 임베딩",
    description="회원가입 정보(해시태그, 성별, 나이대)로 구조화 텍스트를 생성하고 임베딩 결과를 반환한다.",
    response_model=ApiResponse[EmbedTestResponse],
)
async def embed_initial_profile(
    body: InitialProfileRequest,
    model: EmbeddingModelPort = Depends(get_model),
) -> ApiResponse[EmbedTestResponse]:
    text = build_initial_text(body.hashtags, body.gender, body.age_group)
    vector = np.array(model.encode(text))
    return ApiResponse.success(
        EmbedTestResponse(input_text=text, dimension=len(vector), sample=vector[:7].tolist())
    )


class PostEmbedRequest(BaseModel):
    content: str
    hashtags: list[str]


@router.post(
    "/embedding/post",
    summary="게시글 임베딩",
    description="게시글 정보(본문, 태그)로 구조화 텍스트를 생성하고 임베딩 결과를 반환한다.",
    response_model=ApiResponse[EmbedTestResponse],
)
async def embed_post(
    body: PostEmbedRequest,
    model: EmbeddingModelPort = Depends(get_model),
) -> ApiResponse[EmbedTestResponse]:
    text = build_post_text(body.content, body.hashtags)
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
    "/embedding/embed-and-store",
    summary="embed_and_store 직접 호출",
    description="게시글 저장 + 프로필 record_count/active 갱신 로직을 직접 실행한다. "
                "post_id는 자동 생성되며, 결과 프로필 상태를 반환한다.",
    response_model=ApiResponse[EmbedAndStoreResponse],
    status_code=201,
)
async def embed_and_store_test(
    body: EmbedAndStoreRequest,
    svc: EmbeddingService = Depends(get_embedding_service),
) -> ApiResponse[EmbedAndStoreResponse]:
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
