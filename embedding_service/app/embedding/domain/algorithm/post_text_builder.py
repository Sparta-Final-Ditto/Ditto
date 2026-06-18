def build_post_text(
    content: str,
    hashtags: list[str],
    time_slot: str,
    gender: str,
    age_group: str,
) -> str:
    """
    게시글 정보를 구조화된 임베딩용 텍스트로 변환한다.
    예) "오늘 카페에서 혼공했다 | 태그: #혼공 #취준 | 시간대: 저녁 | 성별: 여 | 나이대: 20대"
    """
    tags = " ".join(f"#{tag.lstrip('#')}" for tag in hashtags)  # "#카공", "카공" 모두 허용 — Kafka 형식 확정 후 정리
    return f"{content} | 태그: {tags} | 시간대: {time_slot} | 성별: {gender} | 나이대: {age_group}"
