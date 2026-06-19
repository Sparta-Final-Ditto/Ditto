from datetime import datetime
from uuid import UUID
from pydantic import BaseModel


class EmbeddingStatusResponse(BaseModel):
    post_id: UUID
    embedding_status: str
    embedded_at: datetime | None


class RetryRequest(BaseModel):
    user_id: UUID
    content: str
    hashtags: list[str]
