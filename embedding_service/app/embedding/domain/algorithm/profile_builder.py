def build_initial_text(
    hashtags: list[str],
    gender: str,
    age_group: str,
) -> str:
    """
    회원가입 시 입력한 정보를 임베딩용 텍스트로 변환한다.
    예) "20대 여성 여행 음식 운동"
    """
    tags = " ".join(hashtags)
    return f"{age_group} {gender} {tags}"
