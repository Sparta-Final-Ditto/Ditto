package com.sparta.ditto.assistant.presentation.dto.response;

import com.sparta.ditto.assistant.application.dto.result.AssistantAnswerResult;
import java.util.List;

public record AssistantChatResponse(
        String answer,
        List<SourceResponse> sources
) {
    public record SourceResponse(String title, String sourceType) {}

    public static AssistantChatResponse from(AssistantAnswerResult result) {
        List<SourceResponse> sources = result.sources().stream()
                .map(s -> new SourceResponse(s.title(), s.sourceType()))
                .toList();
        return new AssistantChatResponse(result.answer(), sources);
    }
}
