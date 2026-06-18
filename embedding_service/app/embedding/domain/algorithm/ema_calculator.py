import numpy as np
from app.config.settings import settings


def update_profile(
    current_profile: list[float],
    new_embedding: list[float],
) -> list[float]:
    """EMA로 유저 프로필 벡터를 업데이트한다."""
    alpha = settings.EMA_ALPHA
    profile = np.array(current_profile)
    embedding = np.array(new_embedding)
    updated = alpha * embedding + (1 - alpha) * profile
    return updated.tolist()
