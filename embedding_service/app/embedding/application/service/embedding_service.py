import asyncio
import logging
from datetime import date, datetime, timedelta, timezone
from uuid import UUID

from app.config.settings import settings
from app.embedding.application.port.batch_runner_port import BatchRunnerPort
from app.embedding.application.port.embedding_model_port import EmbeddingModelPort
from app.embedding.domain.algorithm.ema_calculator import average_vectors
from app.embedding.domain.algorithm.post_text_builder import build_post_text
from app.embedding.domain.algorithm.profile_builder import build_initial_text
from app.embedding.domain.model.post_embedding import PostEmbedding
from app.embedding.domain.model.user_profile import UserProfile
from app.embedding.domain.repository.post_embedding_repository import PostEmbeddingRepository
from app.embedding.domain.repository.user_profile_repository import UserProfileRepository

logger = logging.getLogger(__name__)


class EmbeddingService:

    def __init__(
        self,
        post_repo: PostEmbeddingRepository,
        profile_repo: UserProfileRepository,
        model: EmbeddingModelPort,
        batch_runner: BatchRunnerPort,
    ):
        self.post_repo = post_repo
        self.profile_repo = profile_repo
        self.model = model
        self.batch_runner = batch_runner

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
                    last_processed_record_id=existing.last_processed_record_id,
                )
            else:
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

    async def handle_post_deleted(self, post_id: UUID, user_id: UUID) -> None:
        """당일 게시글 삭제 처리 — 당일 게시글만 DELETED로 표시하고 record_count/active를 동기화한다."""
        embedding = await self.post_repo.find_by_post_id(post_id)
        if embedding is None or embedding.embedded_at is None:
            return

        kst = timezone(timedelta(hours=9))
        today_kst = datetime.now(kst).date()
        embedded_date_kst = embedding.embedded_at.astimezone(kst).date()
        # KST 캘린더 날짜가 다르면 이미 다음 배치 처리 대상이 아님 — PASS
        if embedded_date_kst != today_kst:
            logger.info(f"[EmbeddingService] 당일 게시글 아님 — PASS: post_id={post_id}")
            return

        await self.post_repo.update_status(post_id, "DELETED")
        logger.info(f"[EmbeddingService] 당일 게시글 DELETED 처리: post_id={post_id}")

        done_count = await self.post_repo.count_done_by_user_id(user_id)
        await self.profile_repo.sync_count_and_active(user_id, done_count)

    async def handle_post_hard_deleted(self, post_id: UUID, author_id: UUID) -> None:
        """게시글 하드 삭제 처리 — 임베딩 레코드를 삭제하고 필요 시 record_count/active를 보정한다."""
        embedding = await self.post_repo.find_by_post_id(post_id)
        if embedding is None:
            logger.info(f"[EmbeddingService] 임베딩 없음 — PASS: post_id={post_id}")
            return

        await self.post_repo.delete_by_post_id(post_id)
        logger.info(f"[EmbeddingService] hard delete 완료: post_id={post_id}")

        # 엣지케이스: soft delete 이벤트 누락으로 DONE 상태인 경우 record_count 보정
        if embedding.embedding_status == "DONE":
            done_count = await self.post_repo.count_done_by_user_id(author_id)
            await self.profile_repo.sync_count_and_active(author_id, done_count)

    async def handle_post_restored(self, post_id: UUID, author_id: UUID) -> None:
        """게시글 복구 처리 — DELETED 상태였던 게시글만 DONE으로 되돌리고 record_count/active를 갱신한다."""
        embedding = await self.post_repo.find_by_post_id(post_id)
        if embedding is None:
            logger.info(f"[EmbeddingService] 임베딩 없음 — PASS: post_id={post_id}")
            return

        if embedding.embedding_status != "DELETED":
            logger.info(f"[EmbeddingService] DELETED 상태 아님 — PASS: post_id={post_id}, status={embedding.embedding_status}")
            return

        await self.post_repo.update_status(post_id, "DONE")
        logger.info(f"[EmbeddingService] 게시글 복구 DONE 처리: post_id={post_id}")

        # record_count/active 즉시 갱신, 벡터 재계산은 월배치에서 처리
        done_count = await self.post_repo.count_done_by_user_id(author_id)
        await self.profile_repo.sync_count_and_active(author_id, done_count)

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

    async def get_active_user_ids(self) -> list[UUID]:
        """매칭 가능(active=True) 유저 ID 목록 조회."""
        return await self.profile_repo.find_active_user_ids()

    async def get_profile(self, user_id: UUID) -> UserProfile | None:
        return await self.profile_repo.find_by_user_id(user_id)

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

    async def trigger_daily_batch(self) -> None:
        await self.batch_runner.run_daily()

    async def trigger_monthly_batch(self) -> None:
        await self.batch_runner.run_monthly()

    async def embed_text(self, text: str) -> list[float]:
        """임의 텍스트를 벡터로 변환한다."""
        return await asyncio.to_thread(self.model.encode, text)

    async def build_and_embed_initial_profile(
        self,
        hashtags: list[str],
        gender: str,
        age_group: str,
    ) -> tuple[str, list[float]]:
        """구조화 텍스트 생성 후 임베딩 — 생성된 텍스트와 벡터를 함께 반환한다."""
        text = build_initial_text(hashtags, gender, age_group)
        vector = await asyncio.to_thread(self.model.encode, text)
        return text, vector

    async def build_and_embed_post(
        self,
        content: str,
        hashtags: list[str],
    ) -> tuple[str, list[float]]:
        """게시글 구조화 텍스트 생성 후 임베딩 — 생성된 텍스트와 벡터를 함께 반환한다."""
        text = build_post_text(content, hashtags)
        vector = await asyncio.to_thread(self.model.encode, text)
        return text, vector


def _compute_age_group(birthdate: date) -> str:
    from datetime import date as date_cls
    today = date_cls.today()
    age = today.year - birthdate.year - ((today.month, today.day) < (birthdate.month, birthdate.day))
    return f"{(age // 10) * 10}대"
