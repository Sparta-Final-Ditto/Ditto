package com.sparta.ditto.feed.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "댓글 내용을 입력해주세요.")
        @Size(max = 200, message = "댓글은 200자 이내로 입력해주세요.")
        String content
) {
}
