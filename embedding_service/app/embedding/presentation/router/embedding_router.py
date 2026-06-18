import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel, Field
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.domain.algorithm.profile_builder import build_initial_text
from app.embedding.domain.algorithm.post_text_builder import build_post_text

router = APIRouter(tags=["Embedding"])


# ── 공통 응답 ──────────────────────────────────────────────

class EmbedTestResponse(BaseModel):
    input_text: str
    dimension: int
    sample: list[float]  # 768개 중 앞 7개만 샘플로 반환


# ── 기존: 텍스트 직접 입력 ─────────────────────────────────

class RawTextRequest(BaseModel):
    text: str


@router.post(
    "/test",
    summary="[테스트] 텍스트 직접 입력",
    description="텍스트를 직접 입력하여 임베딩 벡터 차원과 샘플을 반환한다.",
    response_model=EmbedTestResponse,
)
async def embed_test(body: RawTextRequest) -> EmbedTestResponse:
    model = ModelLoader.get_model()
    vector = np.array(model.encode(body.text))
    return EmbedTestResponse(input_text=body.text, dimension=len(vector), sample=vector[:7].tolist())


# ── 초기 프로필 텍스트 빌더 테스트 ────────────────────────

class InitialProfileRequest(BaseModel):
    hashtags: list[str]
    gender: str
    age_group: str


@router.post(
    "/test/initial-profile",
    summary="[테스트] 초기 프로필 임베딩",
    description="회원가입 정보(해시태그, 성별, 나이대)로 구조화 텍스트를 생성하고 임베딩 결과를 반환한다.",
    response_model=EmbedTestResponse,
)
async def embed_initial_profile(body: InitialProfileRequest) -> EmbedTestResponse:
    text = build_initial_text(body.hashtags, body.gender, body.age_group)
    model = ModelLoader.get_model()
    vector = np.array(model.encode(text))
    return EmbedTestResponse(input_text=text, dimension=len(vector), sample=vector[:7].tolist())


# ── 게시글 텍스트 빌더 테스트 ──────────────────────────────

class PostEmbedRequest(BaseModel):
    content: str
    hashtags: list[str]
    time_slot: str
    gender: str
    age_group: str


@router.post(
    "/test/post",
    summary="[테스트] 게시글 임베딩",
    description="게시글 정보(본문, 태그, 시간대, 성별, 나이대)로 구조화 텍스트를 생성하고 임베딩 결과를 반환한다.",
    response_model=EmbedTestResponse,
)
async def embed_post(body: PostEmbedRequest) -> EmbedTestResponse:
    text = build_post_text(body.content, body.hashtags, body.time_slot, body.gender, body.age_group)
    model = ModelLoader.get_model()
    vector = np.array(model.encode(text))
    return EmbedTestResponse(input_text=text, dimension=len(vector), sample=vector[:7].tolist())
