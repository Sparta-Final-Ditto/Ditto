from dataclasses import dataclass
from datetime import date, datetime
from uuid import UUID


@dataclass
class UserProfile:
    user_id: UUID
    vector: list[float] | None
    record_count: int
    active: bool
    last_processed_record_id: UUID | None = None
    gender: str | None = None
    birthdate: date | None = None
    updated_at: datetime | None = None
    created_at: datetime | None = None
