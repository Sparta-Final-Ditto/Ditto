"""
새벽 배치 수동 테스트 스크립트.

검증 시나리오:
  1. 테스트 유저 2명 생성 (A: DONE 게시글 5개, B: FAILED 게시글 1개 + DONE 2개)
  2. run_nightly_batch() 실행
  3. 유저 A: EMA로 profile_vector 갱신 + active=True 확인
  4. 유저 B: FAILED → DONE 복구 후 3개 벡터로 EMA 계산 + active=True 확인
  5. 테스트 데이터 정리
"""
import asyncio
import sys
import uuid
from datetime import datetime, timezone

sys.path.insert(0, ".")

from sqlalchemy import delete, text
from app.common.db.database import AsyncSessionLocal
from app.common.db.models import UserPostEmbedding, UserProfileEmbedding
from app.embedding.application.service.batch_service import run_nightly_batch
from app.config.settings import settings


def make_vector(seed: float) -> list[float]:
    """테스트용 768차원 단위 벡터 생성."""
    import numpy as np
    rng = np.random.default_rng(int(seed * 1000))
    v = rng.random(768).astype(float)
    return (v / np.linalg.norm(v)).tolist()


async def seed_data(user_a: uuid.UUID, user_b: uuid.UUID) -> None:
    async with AsyncSessionLocal() as db:
        now = datetime.now(timezone.utc)

        # 유저 A: DONE 게시글 5개
        for i in range(5):
            db.add(UserPostEmbedding(
                post_id=uuid.uuid4(),
                user_id=user_a,
                vector=make_vector(i + 1),
                embedding_status="DONE",
                embedded_at=now,
            ))

        # 유저 B: FAILED 1개 + DONE 2개
        db.add(UserPostEmbedding(
            post_id=uuid.uuid4(),
            user_id=user_b,
            vector=make_vector(10),
            embedding_status="FAILED",
            embedded_at=now,
        ))
        for i in range(2):
            db.add(UserPostEmbedding(
                post_id=uuid.uuid4(),
                user_id=user_b,
                vector=make_vector(11 + i),
                embedding_status="DONE",
                embedded_at=now,
            ))

        await db.commit()
    print(f"[Seed] 유저 A({user_a}): DONE×5")
    print(f"[Seed] 유저 B({user_b}): FAILED×1 + DONE×2")


async def verify(user_a: uuid.UUID, user_b: uuid.UUID) -> None:
    async with AsyncSessionLocal() as db:
        profile_a = await db.get(UserProfileEmbedding, user_a)
        profile_b = await db.get(UserProfileEmbedding, user_b)

        print("\n===== 검증 결과 =====")

        if profile_a:
            print(f"[유저 A] record_count={profile_a.record_count}  active={profile_a.active}  vector_len={len(profile_a.vector)}")
            assert profile_a.record_count == 5, f"FAIL: record_count={profile_a.record_count} (기대: 5)"
            assert profile_a.active is True, "FAIL: active가 True여야 함"
            assert len(profile_a.vector) == 768, "FAIL: vector 차원 불일치"
            print("[유저 A] PASS")
        else:
            print("[유저 A] FAIL: 프로필 없음")

        if profile_b:
            # FAILED 1개 복구 → 총 3개로 EMA 계산
            print(f"[유저 B] record_count={profile_b.record_count}  active={profile_b.active}  vector_len={len(profile_b.vector)}")
            assert profile_b.record_count == 3, f"FAIL: record_count={profile_b.record_count} (기대: 3, FAILED 복구 포함)"
            assert profile_b.active is True, "FAIL: active가 True여야 함"
            print("[유저 B] PASS")
        else:
            print("[유저 B] FAIL: 프로필 없음")

        # FAILED 레코드가 DONE으로 바뀌었는지 확인
        result = await db.execute(
            text("SELECT COUNT(*) FROM user_posts_embeddings WHERE embedding_status = 'FAILED'")
        )
        failed_count = result.scalar()
        print(f"\n[DB] 남은 FAILED 레코드: {failed_count}건 (기대: 0)")
        assert failed_count == 0, f"FAIL: FAILED 레코드 {failed_count}건 남아있음"
        print("[FAILED->DONE 복구] PASS")


async def cleanup(user_a: uuid.UUID, user_b: uuid.UUID) -> None:
    async with AsyncSessionLocal() as db:
        await db.execute(
            delete(UserPostEmbedding).where(
                UserPostEmbedding.user_id.in_([user_a, user_b])
            )
        )
        await db.execute(
            delete(UserProfileEmbedding).where(
                UserProfileEmbedding.user_id.in_([user_a, user_b])
            )
        )
        await db.commit()
    print("\n[Cleanup] 테스트 데이터 삭제 완료")


async def main() -> None:
    user_a = uuid.uuid4()
    user_b = uuid.uuid4()

    print("===== 배치 테스트 시작 =====\n")

    print("[Step 1] 테스트 데이터 삽입")
    await seed_data(user_a, user_b)

    print("\n[Step 2] run_nightly_batch() 실행")
    await run_nightly_batch()

    print("\n[Step 3] 결과 검증")
    await verify(user_a, user_b)

    await cleanup(user_a, user_b)
    print("\n===== 배치 테스트 완료 =====")


if __name__ == "__main__":
    asyncio.run(main())
