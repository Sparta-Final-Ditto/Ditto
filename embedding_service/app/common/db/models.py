import uuid
from sqlalchemy import BigInteger, Boolean, Column, DateTime, Integer, String, func
from sqlalchemy.dialects.postgresql import UUID
from pgvector.sqlalchemy import Vector
from app.common.db.database import Base


class UserPostEmbedding(Base):
    __tablename__ = "user_posts_embeddings"

    id               = Column(BigInteger, primary_key=True, autoincrement=True)
    post_id          = Column(UUID(as_uuid=True), nullable=False, index=True)
    user_id          = Column(UUID(as_uuid=True), nullable=False, index=True)
    vector           = Column(Vector(768), nullable=False)
    embedding_status = Column(String(10), nullable=False, default="PENDING", index=True)
    embedded_at      = Column(DateTime, nullable=True)
    created_at       = Column(DateTime, nullable=False, server_default=func.now())


class UserProfileEmbedding(Base):
    __tablename__ = "user_profile_embeddings"

    user_id                  = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    vector                   = Column(Vector(768), nullable=False)
    record_count             = Column(Integer, nullable=False, default=0)
    active                   = Column(Boolean, nullable=False, default=False)
    last_processed_record_id = Column(UUID(as_uuid=True), nullable=True)
    updated_at               = Column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())
    created_at               = Column(DateTime, nullable=False, server_default=func.now())
