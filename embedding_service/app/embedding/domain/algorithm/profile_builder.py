def build_initial_text(
    hashtags: list[str],
    gender: str,
    age_group: str,
) -> str:
    """
    회원가입 시 입력한 정보를 구조화된 임베딩용 텍스트로 변환한다.
    예) "태그: #여행 #음식 #운동 | 성별: 여 | 나이대: 20대"
    """
    tags = " ".join(f"#{tag.lstrip('#')}" for tag in hashtags)  # "#카공", "카공" 모두 허용 — Kafka 형식 확정 후 정리
    return f"태그: {tags} | 성별: {gender} | 나이대: {age_group}"
