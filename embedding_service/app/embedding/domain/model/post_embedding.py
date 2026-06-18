from dataclasses import dataclass
from datetime import datetime
from uuid import UUID


@dataclass
class PostEmbedding:
    post_id: UUID
    user_id: UUID
    vector: list[float]
    embedding_status: str
    embedded_at: datetime | None = None
    created_at: datetime | None = None
