def build_post_text(content: str, hashtags: list[str]) -> str:
    """
    게시글 정보를 구조화된 임베딩용 텍스트로 변환한다.
    예) "오늘 카페에서 혼공했다 | 태그: #혼공 #취준"
    """
    tags = " ".join(f"#{tag.lstrip('#')}" for tag in hashtags)
    return f"{content} | 태그: {tags}"
