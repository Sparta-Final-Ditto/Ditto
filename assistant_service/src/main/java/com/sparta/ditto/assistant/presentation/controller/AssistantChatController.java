package com.sparta.ditto.assistant.presentation.controller;

import com.sparta.ditto.assistant.application.dto.command.AskAssistantCommand;
import com.sparta.ditto.assistant.application.dto.result.AssistantAnswerResult;
import com.sparta.ditto.assistant.application.service.AssistantChatService;
import com.sparta.ditto.assistant.presentation.dto.request.AssistantChatRequest;
import com.sparta.ditto.assistant.presentation.dto.response.AssistantChatResponse;
import com.sparta.ditto.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "어시스턴트", description = "RAG 기반 고객 응대 챗봇 API")
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantChatController {

    private final AssistantChatService assistantChatService;

    @Operation(summary = "고객 응대 챗봇 질의")
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AssistantChatResponse>> chat(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody AssistantChatRequest request
    ) {
        AssistantAnswerResult result = assistantChatService.ask(
                new AskAssistantCommand(userId, request.question()));
        return ResponseEntity.ok(ApiResponse.success(AssistantChatResponse.from(result)));
    }
}
