from uuid import UUID

from app.config.settings import settings
from app.embedding.application.port.embedding_model_port import EmbeddingModelPort
from app.embedding.domain.algorithm.ema_calculator import update_profile
from app.embedding.domain.algorithm.post_text_builder import build_post_text
from app.embedding.domain.algorithm.profile_builder import build_initial_text
from app.embedding.domain.model.post_embedding import PostEmbedding
from app.embedding.domain.repository.post_embedding_repository import PostEmbeddingRepository
from app.embedding.domain.repository.user_profile_repository import UserProfileRepository


class EmbeddingService:

    def __init__(
        self,
        post_repo: PostEmbeddingRepository,
        profile_repo: UserProfileRepository,
        model: EmbeddingModelPort,
    ):
        self.post_repo = post_repo
        self.profile_repo = profile_repo
        self.model = model

    async def embed_and_store(
        self,
        post_id: UUID,
        user_id: UUID,
        content: str,
        hashtags: list[str],
    ) -> None:
        """게시글 임베딩 생성 및 EMA 프로필 업데이트."""
        text = build_post_text(content, hashtags)
        vector = self.model.encode(text)

        await self.post_repo.save(post_id, user_id, vector)

        existing = await self.profile_repo.find_by_user_id(user_id)
        if existing:
            new_vector = update_profile(existing.vector, vector, settings.EMA_ALPHA)
            new_count = existing.record_count + 1
        else:
            new_vector = vector
            new_count = 1

        await self.profile_repo.upsert(
            user_id=user_id,
            vector=new_vector,
            record_count=new_count,
            active=new_count >= settings.MIN_RECORDS_FOR_MATCHING,
            last_processed_record_id=post_id,
        )

    async def create_initial_profile(
        self,
        user_id: UUID,
        hashtags: list[str],
        gender: str,
        age_group: str,
    ) -> None:
        """회원가입 시 초기 관심사 기반 프로필 벡터 생성."""
        text = build_initial_text(hashtags, gender, age_group)
        vector = self.model.encode(text)
        await self.profile_repo.upsert(
            user_id=user_id,
            vector=vector,
            record_count=0,
            active=False,
        )

    async def get_embedding_status(self, post_id: UUID) -> PostEmbedding | None:
        return await self.post_repo.find_by_post_id(post_id)

    async def retry_embedding(
        self,
        post_id: UUID,
        user_id: UUID,
        content: str,
        hashtags: list[str],
    ) -> None:
        """임베딩 실패 게시글 재처리 — 원본 데이터를 다시 받아 처리한다."""
        await self.embed_and_store(post_id, user_id, content, hashtags)
