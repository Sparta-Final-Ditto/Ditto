package com.sparta.ditto.assistant.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.sparta.ditto.assistant.application.dto.command.AskAssistantCommand;
import com.sparta.ditto.assistant.application.dto.result.AssistantAnswerResult;
import com.sparta.ditto.assistant.application.service.AssistantChatService;
import com.sparta.ditto.assistant.presentation.dto.request.AssistantChatRequest;
import com.sparta.ditto.assistant.presentation.dto.response.AssistantChatResponse;
import com.sparta.ditto.common.response.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AssistantChatControllerTest {

    @Mock
    private AssistantChatService assistantChatService;

    @InjectMocks
    private AssistantChatController assistantChatController;

    @Test
    @DisplayName("POST /api/v1/assistant/chat - 질의 성공 시 200과 답변을 반환한다")
    void chat_returnsOkWithAnswer() {
        UUID userId = UUID.randomUUID();
        AssistantChatRequest request = new AssistantChatRequest("회원가입은 어떻게 하나요?");
        AssistantAnswerResult result = new AssistantAnswerResult(
                "이메일로 가입합니다.",
                List.of(new AssistantAnswerResult.SourceResult("회원가입 방법", "FAQ")));

        given(assistantChatService.ask(eq(new AskAssistantCommand(userId, request.question()))))
                .willReturn(result);

        ResponseEntity<ApiResponse<AssistantChatResponse>> response =
                assistantChatController.chat(userId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().answer()).isEqualTo("이메일로 가입합니다.");
        assertThat(response.getBody().getData().sources()).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/v1/assistant/chat - X-User-Id 없이도(비로그인) 호출할 수 있다")
    void chat_allowsAnonymousRequest() {
        AssistantChatRequest request = new AssistantChatRequest("질문입니다");
        AssistantAnswerResult result = new AssistantAnswerResult("답변입니다", List.of());

        given(assistantChatService.ask(eq(new AskAssistantCommand(null, request.question()))))
                .willReturn(result);

        ResponseEntity<ApiResponse<AssistantChatResponse>> response =
                assistantChatController.chat(null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().answer()).isEqualTo("답변입니다");
    }
}
