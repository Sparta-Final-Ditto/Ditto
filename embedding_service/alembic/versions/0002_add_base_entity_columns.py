"""add base entity columns (updated_at, deleted_at, deleted_by) and timezone support

Revision ID: 0002
Revises: 0001
Create Date: 2026-06-19

"""
from typing import Sequence, Union

from alembic import op

revision: str = "0002"
down_revision: Union[str, None] = "0001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # user_posts_embeddings
    op.execute("""
        ALTER TABLE user_posts_embeddings
            ADD COLUMN updated_at  timestamptz NOT NULL DEFAULT now(),
            ADD COLUMN deleted_at  timestamptz,
            ADD COLUMN deleted_by  uuid,
            ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'UTC',
            ALTER COLUMN embedded_at TYPE timestamptz USING embedded_at AT TIME ZONE 'UTC'
    """)

    # user_profile_embeddings
    op.execute("""
        ALTER TABLE user_profile_embeddings
            ADD COLUMN deleted_at  timestamptz,
            ADD COLUMN deleted_by  uuid,
            ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'UTC',
            ALTER COLUMN updated_at TYPE timestamptz USING updated_at AT TIME ZONE 'UTC'
    """)


def downgrade() -> None:
    op.execute("""
        ALTER TABLE user_posts_embeddings
            DROP COLUMN IF EXISTS updated_at,
            DROP COLUMN IF EXISTS deleted_at,
            DROP COLUMN IF EXISTS deleted_by,
            ALTER COLUMN created_at TYPE timestamp USING created_at AT TIME ZONE 'UTC',
            ALTER COLUMN embedded_at TYPE timestamp USING embedded_at AT TIME ZONE 'UTC'
    """)

    op.execute("""
        ALTER TABLE user_profile_embeddings
            DROP COLUMN IF EXISTS deleted_at,
            DROP COLUMN IF EXISTS deleted_by,
            ALTER COLUMN created_at TYPE timestamp USING created_at AT TIME ZONE 'UTC',
            ALTER COLUMN updated_at TYPE timestamp USING updated_at AT TIME ZONE 'UTC'
    """)
