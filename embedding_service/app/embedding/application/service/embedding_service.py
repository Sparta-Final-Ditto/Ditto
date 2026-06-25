import asyncio
import logging
from datetime import date
from uuid import UUID

from app.config.settings import settings
from app.embedding.application.port.embedding_model_port import EmbeddingModelPort
from app.embedding.domain.algorithm.ema_calculator import average_vectors
from app.embedding.domain.algorithm.post_text_builder import build_post_text
from app.embedding.domain.algorithm.profile_builder import build_initial_text
from app.embedding.domain.model.post_embedding import PostEmbedding
from app.embedding.domain.repository.post_embedding_repository import PostEmbeddingRepository
from app.embedding.domain.repository.user_profile_repository import UserProfileRepository

logger = logging.getLogger(__name__)


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
        """게시글 임베딩 저장 + active 갱신. EMA 벡터 재계산은 새벽 배치에서 처리."""
        text = build_post_text(content, hashtags)
        vector = await asyncio.to_thread(self.model.encode, text)

        is_new = await self.post_repo.find_by_post_id(post_id) is None
        await self.post_repo.save(post_id, user_id, vector)

        if not is_new:
            # 재처리(retry/수정): 벡터만 갱신, record_count는 변경 없음
            return

        try:
            existing = await self.profile_repo.find_by_user_id(user_id)
            if existing:
                new_count = existing.record_count + 1
                # USER_CREATED stub은 vector=NULL → 첫 게시글 벡터로 초기화
                profile_vector = existing.vector if existing.vector is not None else vector
                await self.profile_repo.upsert(
                    user_id=user_id,
                    vector=profile_vector,
                    record_count=new_count,
                    active=new_count >= settings.MIN_RECORDS_FOR_MATCHING,
                    last_processed_record_id=post_id,
                )
            else:
                # 첫 게시글: 배치 전까지 임시 벡터로 프로필 생성
                await self.profile_repo.upsert(
                    user_id=user_id,
                    vector=vector,
                    record_count=1,
                    active=False,
                    last_processed_record_id=post_id,
                )
        except Exception as e:
            # 프로필 갱신 실패는 게시글 임베딩 성공과 무관 — 새벽 배치에서 보정
            logger.warning(f"[EmbeddingService] 프로필 카운트 갱신 실패 (배치에서 복구 예정): {e}")

    async def init_user_profile(
        self,
        user_id: UUID,
        gender: str,
        birthdate: date,
    ) -> None:
        """USER_CREATED: vector=NULL stub 행 생성."""
        await self.profile_repo.init_user_profile(user_id, gender, birthdate)

    async def register_user_interests(
        self,
        user_id: UUID,
        hashtags: list[str],
    ) -> None:
        """USER_INTERESTS_REGISTERED: gender/birthdate 조회 → 관심사 기반 초기 벡터 생성 -> vector만 갱신"""
        profile = await self.profile_repo.find_by_user_id(user_id)
        if profile is None or profile.gender is None or profile.birthdate is None:
            logger.warning(f"[EmbeddingService] USER_CREATED 행 없음, 관심사 임베딩 건너뜀: user_id={user_id}")
            return

        age_group = _compute_age_group(profile.birthdate)
        gender_label = "남" if profile.gender == "MALE" else "여"
        text = build_initial_text(hashtags, gender_label, age_group)
        vector = await asyncio.to_thread(self.model.encode, text)

        await self.profile_repo.update_initial_vector(user_id, vector)

    async def create_initial_profile(
        self,
        user_id: UUID,
        hashtags: list[str],
        gender: str,
        age_group: str,
    ) -> None:
        """회원가입 시 초기 관심사 기반 프로필 벡터 생성."""
        text = build_initial_text(hashtags, gender, age_group)
        vector = await asyncio.to_thread(self.model.encode, text)
        await self.profile_repo.upsert(
            user_id=user_id,
            vector=vector,
            record_count=0,
            active=False,
        )

    async def get_embedding_status(self, post_id: UUID) -> PostEmbedding | None:
        return await self.post_repo.find_by_post_id(post_id)

    async def get_profile_vector(self, user_id: UUID):
        """match_service 연동용 — V_batch(프로필)와 V_today(오늘 게시글 평균) 반환."""
        profile = await self.profile_repo.find_by_user_id(user_id)
        if profile is None:
            return None, None

        today_vectors = await self.post_repo.find_today_vectors(user_id)
        today_vector = average_vectors(today_vectors) if today_vectors else None

        return profile, today_vector

    async def get_profiles_batch(self, user_ids: list[UUID]) -> list[tuple]:
        """match_service Spring Batch용 — 최대 100명 일괄 조회."""
        results = []
        for user_id in user_ids:
            profile, today_vector = await self.get_profile_vector(user_id)
            if profile is not None:
                results.append((profile, today_vector))
        return results

    async def retry_embedding(
        self,
        post_id: UUID,
        user_id: UUID,
        content: str,
        hashtags: list[str],
    ) -> None:
        """임베딩 실패 게시글 재처리 — 원본 데이터를 다시 받아 처리한다."""
        await self.embed_and_store(post_id, user_id, content, hashtags)


def _compute_age_group(birthdate: date) -> str:
    from datetime import date as date_cls
    today = date_cls.today()
    age = today.year - birthdate.year - ((today.month, today.day) < (birthdate.month, birthdate.day))
    return f"{(age // 10) * 10}대"
