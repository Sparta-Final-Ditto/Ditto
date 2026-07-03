package com.sparta.ditto.assistant.application.dto.result;

import java.util.List;

public record AssistantAnswerResult(
        String answer,
        List<SourceResult> sources
) {
    public record SourceResult(String title, String sourceType) {}
}
