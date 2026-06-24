import numpy as np
from unittest.mock import AsyncMock, MagicMock

from app.embedding.application.port.embedding_model_port import EmbeddingModelPort

_v = np.ones(768)
FAKE_VECTOR: list[float] = (_v / np.linalg.norm(_v)).tolist()


class FakeModel(EmbeddingModelPort):
    """실제 ML 모델 없이 항상 같은 768차원 단위 벡터를 반환."""
    def encode(self, text: str) -> list[float]:
        return FAKE_VECTOR


def make_profile(
    record_count: int = 0,
    active: bool = False,
    vector: list[float] | None = None,
    last_processed_record_id=None,
    gender: str | None = "FEMALE",
    birthdate=None,
) -> MagicMock:
    from datetime import date
    p = MagicMock()
    p.record_count = record_count
    p.active = active
    p.vector = vector or FAKE_VECTOR
    p.last_processed_record_id = last_processed_record_id
    p.gender = gender
    p.birthdate = birthdate or date(1999, 5, 20)
    return p


def make_vectors(n: int) -> list[list[float]]:
    """n개의 서로 다른 768차원 단위 벡터 생성."""
    result = []
    for i in range(n):
        v = np.ones(768) * (i + 1)
        result.append((v / np.linalg.norm(v)).tolist())
    return result


def new_post_repo() -> AsyncMock:
    repo = AsyncMock()
    repo.find_by_post_id.return_value = None
    repo.find_today_vectors.return_value = []
    return repo


def new_profile_repo() -> AsyncMock:
    repo = AsyncMock()
    repo.find_by_user_id.return_value = None
    return repo
