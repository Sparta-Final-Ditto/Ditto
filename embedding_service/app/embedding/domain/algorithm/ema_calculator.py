import math
from datetime import datetime, timezone

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


def time_decay_weighted_average(
    records: list[tuple[list[float], datetime]],
) -> list[float] | None:
    """시간 감쇠 가중 평균으로 프로필 벡터를 계산한다. (매월 1일 배치)

    weight = exp(-age_days / 7) —> 7일 반감 스케일.
    records: (vector, embedded_at) 리스트.
    게시글이 없으면 None 반환.
    """
    if not records:
        return None

    now = datetime.now(timezone.utc)
    weighted_sum = np.zeros(len(records[0][0]))
    total_weight = 0.0

    for vector, embedded_at in records:
        age_days = (now - embedded_at.astimezone(timezone.utc)).total_seconds() / 86400
        weight = math.exp(-age_days / 7)
        weighted_sum += weight * np.array(vector)
        total_weight += weight

    if total_weight == 0:
        return None

    result = weighted_sum / total_weight
    norm = np.linalg.norm(result)
    return (result / norm).tolist() if norm > 0 else result.tolist()
