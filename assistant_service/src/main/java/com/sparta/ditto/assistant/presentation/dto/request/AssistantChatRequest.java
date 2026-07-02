package com.sparta.ditto.assistant.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AssistantChatRequest(
        @NotBlank(message = "질문을 입력해주세요.")
        String question
) {
}
