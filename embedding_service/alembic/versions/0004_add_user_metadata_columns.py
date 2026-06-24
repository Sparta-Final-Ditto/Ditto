"""add gender, birthdate to user_profile_embeddings; make vector nullable

Revision ID: 0004
Revises: 0003
Create Date: 2026-06-24

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "0004"
down_revision: Union[str, None] = "0003"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("user_profile_embeddings", sa.Column("gender", sa.String(6), nullable=True))
    op.add_column("user_profile_embeddings", sa.Column("birthdate", sa.Date(), nullable=True))
    op.alter_column("user_profile_embeddings", "vector", existing_nullable=False, nullable=True)


def downgrade() -> None:
    op.alter_column("user_profile_embeddings", "vector", existing_nullable=True, nullable=False)
    op.drop_column("user_profile_embeddings", "birthdate")
    op.drop_column("user_profile_embeddings", "gender")
