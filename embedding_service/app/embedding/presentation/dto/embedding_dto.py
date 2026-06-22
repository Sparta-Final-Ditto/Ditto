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


class ProfileVectorResponse(BaseModel):
    user_id: UUID
    profile_vector: list[float]
    today_vector: list[float] | None
    active: bool
    record_count: int


class ActiveUserIdsResponse(BaseModel):
    user_ids: list[UUID]
    count: int


class ProfileBatchRequest(BaseModel):
    user_ids: list[UUID]


class ProfileBatchItem(BaseModel):
    user_id: UUID
    profile_vector: list[float]
    today_vector: list[float] | None


class ProfileBatchResponse(BaseModel):
    profiles: list[ProfileBatchItem]
