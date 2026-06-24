import unittest

from app.embedding.domain.algorithm.post_text_builder import build_post_text
from app.embedding.domain.algorithm.profile_builder import build_initial_text


class TestBuildPostText(unittest.TestCase):

    def test_basic_format(self):
        result = build_post_text("오늘 카페에서 혼공했다", ["혼공", "취준"])
        self.assertEqual(result, "오늘 카페에서 혼공했다 | 태그: #혼공 #취준")

    def test_empty_hashtags(self):
        result = build_post_text("내용만 있어요", [])
        self.assertEqual(result, "내용만 있어요 | 태그: ")

    def test_hashtag_prefix_already_present(self):
        """'#' 접두사가 이미 붙은 태그도 중복 없이 처리."""
        result = build_post_text("게시글", ["#카공", "취준"])
        self.assertIn("#카공", result)
        self.assertIn("#취준", result)
        self.assertNotIn("##", result)

    def test_single_hashtag(self):
        result = build_post_text("내용", ["운동"])
        self.assertEqual(result, "내용 | 태그: #운동")

    def test_content_preserved_exactly(self):
        content = "한강에서 자전거 탔는데 진짜 너무 좋았다"
        result = build_post_text(content, [])
        self.assertTrue(result.startswith(content))


class TestBuildInitialText(unittest.TestCase):

    def test_basic_format(self):
        result = build_initial_text(["여행", "음식"], "여", "20대")
        self.assertEqual(result, "태그: #여행 #음식 | 성별: 여 | 나이대: 20대")

    def test_empty_hashtags(self):
        result = build_initial_text([], "남", "30대")
        self.assertEqual(result, "태그:  | 성별: 남 | 나이대: 30대")

    def test_hashtag_prefix_already_present(self):
        result = build_initial_text(["#등산", "캠핑"], "여", "20대")
        self.assertIn("#등산", result)
        self.assertIn("#캠핑", result)
        self.assertNotIn("##", result)

    def test_gender_and_age_group_in_output(self):
        result = build_initial_text([], "MALE", "20s")
        self.assertIn("MALE", result)
        self.assertIn("20s", result)

    def test_single_hashtag(self):
        result = build_initial_text(["독서"], "여", "40대")
        self.assertIn("#독서", result)


if __name__ == "__main__":
    unittest.main()
