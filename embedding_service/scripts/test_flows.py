"""
embedding_service_flow.md 기반 통합 흐름 검증 스크립트

Flow 1. 회원가입 - 초기 프로필 생성
Flow 2. 게시글 업로드 - embed_and_store (record_count/active 실시간 갱신)
Flow 3. 새벽 배치 - FAILED 복구 + EMA 재계산
"""
import asyncio
import sys
import uuid
from datetime import datetime, timezone

sys.path.insert(0, ".")

from sqlalchemy import delete, text
from app.common.db.database import AsyncSessionLocal
from app.common.db.models import UserPostEmbedding, UserProfileEmbedding
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.application.service.batch_service import run_nightly_batch
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.infrastructure.repository.pg_post_embedding_repository import PgPostEmbeddingRepository
from app.embedding.infrastructure.repository.pg_user_profile_repository import PgUserProfileRepository
from app.config.settings import settings

SEPARATOR = "-" * 50


def section(title: str) -> None:
    print(f"\n{'=' * 50}")
    print(f"  {title}")
    print(f"{'=' * 50}")


def ok(msg: str) -> None:
    print(f"  [PASS] {msg}")


def fail(msg: str) -> None:
    print(f"  [FAIL] {msg}")
    raise AssertionError(msg)


def check(condition: bool, msg: str) -> None:
    if condition:
        ok(msg)
    else:
        fail(msg)


async def cleanup(user_ids: list[uuid.UUID]) -> None:
    async with AsyncSessionLocal() as db:
        await db.execute(
            delete(UserPostEmbedding).where(UserPostEmbedding.user_id.in_(user_ids))
        )
        await db.execute(
            delete(UserProfileEmbedding).where(UserProfileEmbedding.user_id.in_(user_ids))
        )
        await db.commit()


# ── Flow 1 ──────────────────────────────────────────────────────────────

async def test_flow1_initial_profile() -> uuid.UUID:
    section("Flow 1. 회원가입 - 초기 프로필 생성")

    user_id = uuid.uuid4()
    print(f"  user_id: {user_id}")

    async with AsyncSessionLocal() as db:
        svc = EmbeddingService(
            post_repo=PgPostEmbeddingRepository(db),
            profile_repo=PgUserProfileRepository(db),
            model=ModelLoader(),
        )
        await svc.create_initial_profile(
            user_id=user_id,
            hashtags=["등산", "자연", "힐링"],
            gender="MALE",
            age_group="20s",
        )

    async with AsyncSessionLocal() as db:
        profile = await db.get(UserProfileEmbedding, user_id)

    check(profile is not None, "user_profile_embeddings 레코드 생성됨")
    assert profile is not None
    check(profile.record_count == 0, f"record_count=0 (실제: {profile.record_count})")
    check(profile.active is False, f"active=False (실제: {profile.active})")
    check(len(profile.vector) == 768, f"벡터 768차원 (실제: {len(profile.vector)})")

    print(f"\n  vector sample: {[round(v, 4) for v in profile.vector[:5]]}...")
    return user_id


# ── Flow 2 ──────────────────────────────────────────────────────────────

async def test_flow2_post_embedding(user_id: uuid.UUID) -> None:
    section("Flow 2. 게시글 업로드 - embed_and_store 실시간 갱신")

    posts = [
        ("한강에서 자전거 탔는데 진짜 너무 좋았다", ["한강", "자전거", "취미"]),
        ("오늘 북한산 등산 완등했어요 뿌듯하네요", ["등산", "북한산", "운동"]),
        ("주말에 캠핑 다녀왔는데 별이 너무 예뻤어", ["캠핑", "자연", "힐링"]),
    ]

    for i, (content, hashtags) in enumerate(posts, start=1):
        post_id = uuid.uuid4()
        async with AsyncSessionLocal() as db:
            svc = EmbeddingService(
                post_repo=PgPostEmbeddingRepository(db),
                profile_repo=PgUserProfileRepository(db),
                model=ModelLoader(),
            )
            await svc.embed_and_store(post_id, user_id, content, hashtags)

        async with AsyncSessionLocal() as db:
            profile = await db.get(UserProfileEmbedding, user_id)
            post_row = await db.execute(
                text("SELECT embedding_status FROM user_posts_embeddings WHERE post_id = :pid"),
                {"pid": str(post_id)},
            )
            status = post_row.scalar()

        assert profile is not None
        print(f"\n  [{i}번째 게시글] \"{content[:20]}...\"")
        check(status == "DONE", f"user_posts_embeddings status=DONE (실제: {status})")
        check(profile.record_count == i, f"record_count={i} (실제: {profile.record_count})")

        expected_active = i >= settings.MIN_RECORDS_FOR_MATCHING
        check(
            profile.active is expected_active,
            f"active={expected_active} (실제: {profile.active}) "
            f"[MIN_RECORDS_FOR_MATCHING={settings.MIN_RECORDS_FOR_MATCHING}]",
        )

    print(f"\n  3번째 게시글 업로드 후 active=True 전환 확인 완료")

    # upsert 검증 - 동일 post_id 재처리 시 record_count 불변
    print(f"\n  [upsert/retry 검증] 동일 post_id 재처리 시 record_count 불변")
    retry_post_id = uuid.uuid4()
    async with AsyncSessionLocal() as db:
        svc = EmbeddingService(
            post_repo=PgPostEmbeddingRepository(db),
            profile_repo=PgUserProfileRepository(db),
            model=ModelLoader(),
        )
        await svc.embed_and_store(retry_post_id, user_id, "최초 게시글", [])

    async with AsyncSessionLocal() as db:
        before_profile = await db.get(UserProfileEmbedding, user_id)
    assert before_profile is not None
    count_before = before_profile.record_count

    async with AsyncSessionLocal() as db:
        svc = EmbeddingService(
            post_repo=PgPostEmbeddingRepository(db),
            profile_repo=PgUserProfileRepository(db),
            model=ModelLoader(),
        )
        await svc.embed_and_store(retry_post_id, user_id, "수정된 게시글", [])

    async with AsyncSessionLocal() as db:
        after_profile = await db.get(UserProfileEmbedding, user_id)
    assert after_profile is not None

    check(
        after_profile.record_count == count_before,
        f"retry 후 record_count 불변 (before={count_before}, after={after_profile.record_count})",
    )


# ── Flow 3 ──────────────────────────────────────────────────────────────

async def test_flow3_nightly_batch() -> uuid.UUID:
    section("Flow 3. 새벽 배치 - FAILED 복구 + EMA 재계산")

    user_id = uuid.uuid4()
    print(f"  user_id: {user_id}")

    import numpy as np

    def make_vector(seed: int) -> list[float]:
        rng = np.random.default_rng(seed)
        v = rng.random(768).astype(float)
        return (v / np.linalg.norm(v)).tolist()

    async with AsyncSessionLocal() as db:
        now = datetime.now(timezone.utc)
        # DONE 3개 + FAILED 2개 삽입
        for i in range(3):
            db.add(UserPostEmbedding(
                post_id=uuid.uuid4(), user_id=user_id,
                vector=make_vector(i), embedding_status="DONE", embedded_at=now,
            ))
        for i in range(3, 5):
            db.add(UserPostEmbedding(
                post_id=uuid.uuid4(), user_id=user_id,
                vector=make_vector(i), embedding_status="FAILED", embedded_at=now,
            ))
        await db.commit()

    print("  DONE x3 + FAILED x2 삽입 완료")

    await run_nightly_batch()

    async with AsyncSessionLocal() as db:
        profile = await db.get(UserProfileEmbedding, user_id)
        failed_result = await db.execute(
            text("SELECT COUNT(*) FROM user_posts_embeddings WHERE user_id = :uid AND embedding_status = 'FAILED'"),
            {"uid": str(user_id)},
        )
        remaining_failed = failed_result.scalar()

    check(remaining_failed == 0, f"FAILED 레코드 전부 DONE으로 복구 (남은 FAILED: {remaining_failed})")
    check(profile is not None, "user_profile_embeddings 생성됨")
    assert profile is not None
    check(profile.record_count == 5, f"record_count=5 (DONE 3 + 복구 2, 실제: {profile.record_count})")
    check(profile.active is True, f"active=True (실제: {profile.active})")
    check(len(profile.vector) == 768, f"EMA 벡터 768차원 (실제: {len(profile.vector)})")

    print(f"\n  EMA vector sample: {[round(v, 4) for v in profile.vector[:5]]}...")
    return user_id


# ── main ─────────────────────────────────────────────────────────────────

async def main() -> None:
    print("임베딩 서비스 흐름 통합 검증 시작")
    print("모델 로딩 중...")
    ModelLoader.load()

    flow1_user = None
    flow3_user = None

    try:
        flow1_user = await test_flow1_initial_profile()
        await test_flow2_post_embedding(flow1_user)
        flow3_user = await test_flow3_nightly_batch()

        print(f"\n{'=' * 50}")
        print("  전체 검증 완료")
        print(f"{'=' * 50}\n")

    finally:
        users = [u for u in [flow1_user, flow3_user] if u is not None]
        if users:
            await cleanup(users)
            print("테스트 데이터 정리 완료")


if __name__ == "__main__":
    asyncio.run(main())
