import math
import unittest
import numpy as np

from app.embedding.domain.algorithm.ema_calculator import update_profile, average_vectors


def unit_vector(dim: int = 768, seed: int = 0) -> list[float]:
    rng = np.random.default_rng(seed)
    v = rng.random(dim)
    return (v / np.linalg.norm(v)).tolist()


class TestUpdateProfile(unittest.TestCase):

    def test_result_is_unit_vector(self):
        """EMA 결과는 항상 단위 벡터(norm≈1)여야 한다."""
        result = update_profile(unit_vector(seed=0), unit_vector(seed=1), alpha=0.1)
        norm = np.linalg.norm(result)
        self.assertAlmostEqual(norm, 1.0, places=6)

    def test_alpha_0_returns_current_profile(self):
        """alpha=0 → 기존 프로필 벡터 유지."""
        current = unit_vector(seed=0)
        new = unit_vector(seed=1)
        result = update_profile(current, new, alpha=0.0)
        self.assertTrue(np.allclose(result, current))

    def test_alpha_1_returns_new_embedding(self):
        """alpha=1 → 새 임베딩 벡터로 완전 교체."""
        current = unit_vector(seed=0)
        new = unit_vector(seed=1)
        result = update_profile(current, new, alpha=1.0)
        self.assertTrue(np.allclose(result, new))

    def test_alpha_between_blends_vectors(self):
        """0 < alpha < 1 → 가중 평균 방향으로 이동한다."""
        current = [1.0] + [0.0] * 767
        new = [0.0, 1.0] + [0.0] * 766
        result = update_profile(current, new, alpha=0.5)
        # 두 방향의 중간이므로 첫 두 원소가 비슷해야 함
        self.assertAlmostEqual(result[0], result[1], places=5)

    def test_repeated_updates_stay_unit_vector(self):
        """반복 EMA 업데이트 후에도 norm=1 유지."""
        profile = unit_vector(seed=0)
        for i in range(1, 20):
            profile = update_profile(profile, unit_vector(seed=i), alpha=0.1)
        norm = np.linalg.norm(profile)
        self.assertAlmostEqual(norm, 1.0, places=5)

    def test_zero_vector_safe(self):
        """결과가 zero vector일 때 ZeroDivisionError 없음."""
        zero = [0.0] * 768
        result = update_profile(zero, zero, alpha=0.5)
        self.assertEqual(len(result), 768)

    def test_output_length_matches_input(self):
        """출력 벡터 차원이 입력과 동일하다."""
        result = update_profile(unit_vector(), unit_vector(seed=1), alpha=0.1)
        self.assertEqual(len(result), 768)


class TestAverageVectors(unittest.TestCase):

    def test_single_vector_returns_itself(self):
        """벡터 1개 → 자기 자신(단위 벡터) 반환."""
        v = unit_vector(seed=0)
        result = average_vectors([v])
        self.assertTrue(np.allclose(result, v))

    def test_result_is_unit_vector(self):
        """평균 결과는 단위 벡터여야 한다."""
        vectors = [unit_vector(seed=i) for i in range(5)]
        result = average_vectors(vectors)
        norm = np.linalg.norm(result)
        self.assertAlmostEqual(norm, 1.0, places=6)

    def test_two_identical_vectors_returns_same(self):
        """동일 벡터 두 개 → 그 벡터 반환."""
        v = unit_vector(seed=42)
        result = average_vectors([v, v])
        self.assertTrue(np.allclose(result, v))

    def test_output_length(self):
        """출력 차원이 입력과 동일하다."""
        result = average_vectors([unit_vector(seed=i) for i in range(3)])
        self.assertEqual(len(result), 768)


if __name__ == "__main__":
    unittest.main()
