from fastapi import APIRouter
from pydantic import BaseModel
from app.embedding.infrastructure.model.model_loader import ModelLoader

router = APIRouter(tags=["Embedding"])


class EmbedTestRequest(BaseModel):
    text: str


class EmbedTestResponse(BaseModel):
    dimension: int
    sample: list[float]


@router.post(
    "/test",
    summary="임베딩 계산 테스트",
    description="텍스트를 입력하면 임베딩 벡터 차원과 앞 7개 샘플 값을 반환한다.",
    response_model=EmbedTestResponse,
)
async def embed_test(body: EmbedTestRequest) -> EmbedTestResponse:
    model = ModelLoader.get_model()
    vector = model.encode(body.text)
    return EmbedTestResponse(dimension=len(vector), sample=vector[:7].tolist())  # 768개 중 앞 7개만 샘플로 반환
