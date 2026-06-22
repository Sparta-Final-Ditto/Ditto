"""
embed_and_store is_new 체크 검증 스크립트

검증 항목:
  1. 신규 post_id → record_count 증가
  2. 동일 post_id 재처리 (retry) → record_count 불변, 벡터 업데이트
  3. 신규 post_id 추가 → record_count 다시 증가
  4. active 전환 정확성 (MIN_RECORDS_FOR_MATCHING 기준)
"""
import asyncio
import sys
import uuid
from sqlalchemy import delete

sys.path.insert(0, ".")

from app.common.db.database import AsyncSessionLocal
from app.common.db.models import UserPostEmbedding, UserProfileEmbedding
from app.embedding.application.service.embedding_service import EmbeddingService
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.infrastructure.repository.pg_post_embedding_repository import PgPostEmbeddingRepository
from app.embedding.infrastructure.repository.pg_user_profile_repository import PgUserProfileRepository
from app.config.settings import settings


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


def section(title: str) -> None:
    print(f"\n{'=' * 55}")
    print(f"  {title}")
    print(f"{'=' * 55}")


async def make_svc(db) -> EmbeddingService:
    return EmbeddingService(
        post_repo=PgPostEmbeddingRepository(db),
        profile_repo=PgUserProfileRepository(db),
        model=ModelLoader(),
    )


async def get_profile(user_id: uuid.UUID) -> UserProfileEmbedding | None:
    async with AsyncSessionLocal() as db:
        return await db.get(UserProfileEmbedding, user_id)


async def get_post_vector(post_id: uuid.UUID) -> list[float] | None:
    from sqlalchemy import select
    async with AsyncSessionLocal() as db:
        result = await db.execute(
            select(UserPostEmbedding).where(UserPostEmbedding.post_id == post_id)
        )
        row = result.scalar_one_or_none()
        return row.vector if row else None


async def cleanup(user_id: uuid.UUID) -> None:
    async with AsyncSessionLocal() as db:
        await db.execute(
            delete(UserPostEmbedding).where(UserPostEmbedding.user_id == user_id)
        )
        await db.execute(
            delete(UserProfileEmbedding).where(UserProfileEmbedding.user_id == user_id)
        )
        await db.commit()


# ── 테스트 ────────────────────────────────────────────────────────────────────

async def test_new_post_increments_count(user_id: uuid.UUID) -> uuid.UUID:
    section("Case 1. 신규 post_id → record_count 증가")

    post_id = uuid.uuid4()
    async with AsyncSessionLocal() as db:
        svc = await make_svc(db)
        await svc.embed_and_store(post_id, user_id, "첫 번째 게시글 내용", ["여행", "힐링"])

    profile = await get_profile(user_id)
    check(profile is not None, "프로필 레코드 생성됨")
    assert profile is not None
    check(profile.record_count == 1, f"record_count=1 (실제: {profile.record_count})")
    check(profile.active is False, f"active=False (MIN={settings.MIN_RECORDS_FOR_MATCHING})")
    check(len(profile.vector) == 768, f"벡터 768차원 (실제: {len(profile.vector)})")

    return post_id


async def test_retry_does_not_increment(user_id: uuid.UUID, post_id: uuid.UUID) -> None:
    section("Case 2. 동일 post_id retry → record_count 불변, 벡터 업데이트")

    vector_before = await get_post_vector(post_id)

    async with AsyncSessionLocal() as db:
        svc = await make_svc(db)
        await svc.embed_and_store(post_id, user_id, "수정된 게시글 내용 (retry)", ["등산", "캠핑"])

    profile = await get_profile(user_id)
    assert profile is not None
    vector_after = await get_post_vector(post_id)

    check(profile.record_count == 1, f"retry 후 record_count 불변=1 (실제: {profile.record_count})")
    check(profile.active is False, f"retry 후 active 불변=False (실제: {profile.active})")
    check(vector_after is not None, "벡터 레코드 존재")
    import numpy as np
    check(vector_before is not None and vector_after is not None, "before/after 벡터 둘 다 존재")
    vectors_differ = not np.allclose(np.array(vector_before), np.array(vector_after))
    check(vectors_differ, "벡터 업데이트됨 (콘텐츠 변경 → 새 임베딩 반영)")


async def test_new_post_again_increments(user_id: uuid.UUID) -> None:
    section("Case 3. 새 post_id 추가 → record_count 다시 증가")

    for i in range(2, settings.MIN_RECORDS_FOR_MATCHING + 1):
        post_id = uuid.uuid4()
        async with AsyncSessionLocal() as db:
            svc = await make_svc(db)
            await svc.embed_and_store(post_id, user_id, f"{i}번째 새 게시글", ["운동"])

        profile = await get_profile(user_id)
        assert profile is not None
        expected_active = i >= settings.MIN_RECORDS_FOR_MATCHING
        check(profile.record_count == i, f"record_count={i} (실제: {profile.record_count})")
        check(
            profile.active is expected_active,
            f"active={expected_active} at count={i} (실제: {profile.active})",
        )

    print(f"\n  {settings.MIN_RECORDS_FOR_MATCHING}번째 게시글 업로드 후 active=True 전환 확인")


async def test_multiple_retries_stable(user_id: uuid.UUID) -> None:
    section("Case 4. 동일 post_id 여러 번 retry → record_count 계속 불변")

    profile_before = await get_profile(user_id)
    assert profile_before is not None
    count_before = profile_before.record_count

    post_id = uuid.uuid4()
    async with AsyncSessionLocal() as db:
        svc = await make_svc(db)
        await svc.embed_and_store(post_id, user_id, "새 게시글", [])

    profile_after_new = await get_profile(user_id)
    assert profile_after_new is not None
    check(
        profile_after_new.record_count == count_before + 1,
        f"신규 post 후 record_count={count_before + 1} (실제: {profile_after_new.record_count})",
    )
    count_after_new = profile_after_new.record_count

    for i in range(1, 4):
        async with AsyncSessionLocal() as db:
            svc = await make_svc(db)
            await svc.embed_and_store(post_id, user_id, f"retry #{i}", [])

        profile = await get_profile(user_id)
        assert profile is not None
        check(
            profile.record_count == count_after_new,
            f"retry #{i} 후 record_count 불변={count_after_new} (실제: {profile.record_count})",
        )


# ── main ──────────────────────────────────────────────────────────────────────

async def main() -> None:
    print("embed_and_store is_new 체크 검증 시작")
    print("모델 로딩 중...")
    ModelLoader.load()

    user_id = uuid.uuid4()
    print(f"테스트 user_id: {user_id}\n")

    try:
        post_id = await test_new_post_increments_count(user_id)
        await test_retry_does_not_increment(user_id, post_id)
        await test_new_post_again_increments(user_id)
        await test_multiple_retries_stable(user_id)

        print(f"\n{'=' * 55}")
        print("  전체 검증 완료")
        print(f"{'=' * 55}\n")

    finally:
        await cleanup(user_id)
        print("테스트 데이터 정리 완료")


if __name__ == "__main__":
    asyncio.run(main())
