"""add unique constraint on user_posts_embeddings.post_id

Revision ID: 0003
Revises: 0002
Create Date: 2026-06-21

"""
from typing import Sequence, Union

from alembic import op

revision: str = "0003"
down_revision: Union[str, None] = "0002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute("""
        ALTER TABLE user_posts_embeddings
            ADD CONSTRAINT uq_user_posts_embeddings_post_id UNIQUE (post_id)
    """)


def downgrade() -> None:
    op.execute("""
        ALTER TABLE user_posts_embeddings
            DROP CONSTRAINT IF EXISTS uq_user_posts_embeddings_post_id
    """)
