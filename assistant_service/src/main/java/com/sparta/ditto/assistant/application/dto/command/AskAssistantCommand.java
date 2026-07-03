package com.sparta.ditto.assistant.application.dto.command;

import java.util.UUID;

public record AskAssistantCommand(
        UUID userId,
        String question
) {
}
