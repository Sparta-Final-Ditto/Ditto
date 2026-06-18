from dataclasses import dataclass
from datetime import datetime
from uuid import UUID


@dataclass
class UserProfile:
    user_id: UUID
    vector: list[float]
    record_count: int
    active: bool
    last_processed_record_id: UUID | None = None
    updated_at: datetime | None = None
    created_at: datetime | None = None
