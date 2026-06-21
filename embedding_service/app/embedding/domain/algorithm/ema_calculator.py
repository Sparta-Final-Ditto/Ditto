import numpy as np


def update_profile(
    current_profile: list[float],
    new_embedding: list[float],
    alpha: float,
) -> list[float]:
    """EMA로 유저 프로필 벡터를 업데이트하고 단위 벡터로 정규화한다."""
    profile = np.array(current_profile)
    embedding = np.array(new_embedding)
    updated = alpha * embedding + (1 - alpha) * profile
    norm = np.linalg.norm(updated)
    return (updated / norm).tolist() if norm > 0 else updated.tolist()


def average_vectors(vectors: list[list[float]]) -> list[float]:
    """벡터 리스트의 산술 평균을 계산하고 단위 벡터로 정규화한다."""
    arr = np.mean([np.array(v) for v in vectors], axis=0)
    norm = np.linalg.norm(arr)
    return (arr / norm).tolist() if norm > 0 else arr.tolist()
