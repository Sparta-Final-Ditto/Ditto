"""initial schema

Revision ID: 0001
Revises:
Create Date: 2026-06-17

"""
from typing import Sequence, Union

from alembic import op

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")

    op.execute("""
        CREATE TABLE user_posts_embeddings (
            id          bigserial    PRIMARY KEY,
            post_id     uuid         NOT NULL,
            user_id     uuid         NOT NULL,
            vector      vector(768)  NOT NULL,
            embedding_status varchar(10) NOT NULL DEFAULT 'PENDING',
            embedded_at timestamp,
            created_at  timestamp    NOT NULL DEFAULT now()
        )
    """)
    op.execute("CREATE INDEX ON user_posts_embeddings (post_id)")
    op.execute("CREATE INDEX ON user_posts_embeddings (user_id)")
    op.execute("CREATE INDEX ON user_posts_embeddings (embedding_status)")

    op.execute("""
        CREATE TABLE user_profile_embeddings (
            user_id                  uuid         PRIMARY KEY,
            vector                   vector(768)  NOT NULL,
            record_count             int          NOT NULL DEFAULT 0,
            active                   boolean      NOT NULL DEFAULT false,
            last_processed_record_id uuid,
            updated_at               timestamp    NOT NULL DEFAULT now(),
            created_at               timestamp    NOT NULL DEFAULT now()
        )
    """)


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS user_profile_embeddings")
    op.execute("DROP TABLE IF EXISTS user_posts_embeddings")
