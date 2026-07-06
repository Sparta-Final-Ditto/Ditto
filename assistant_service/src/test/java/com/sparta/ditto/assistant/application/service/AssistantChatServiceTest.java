package com.sparta.ditto.assistant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.assistant.application.dto.command.AskAssistantCommand;
import com.sparta.ditto.assistant.application.dto.result.AssistantAnswerResult;
import com.sparta.ditto.assistant.application.port.AssistantAnswerPort;
import com.sparta.ditto.assistant.application.port.AssistantAnswerPort.AssistantAnswer;
import com.sparta.ditto.assistant.application.port.AssistantAnswerPort.RetrievedDocument;
import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import com.sparta.ditto.assistant.domain.exception.LlmResponseFailedException;
import com.sparta.ditto.assistant.domain.repository.AssistantChatLogRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssistantChatServiceTest {

    @Mock
    private AssistantAnswerPort assistantAnswerPort;

    @Mock
    private AssistantChatLogRepository chatLogRepository;

    @InjectMocks
    private AssistantChatService assistantChatService;

    @Test
    @DisplayName("ask() 성공 시 검색된 문서를 출처로 채워 답변을 반환하고 로그를 저장한다")
    void ask_success_returnsAnswerWithSourcesAndSavesLog() {
        UUID userId = UUID.randomUUID();
        AskAssistantCommand command = new AskAssistantCommand(userId, "회원가입은 어떻게 하나요?");
        UUID documentId = UUID.randomUUID();
        AssistantAnswer answer = new AssistantAnswer(
                "이메일로 가입할 수 있습니다.",
                List.of(new RetrievedDocument(documentId, "회원가입 방법", "FAQ", 0.9f)));

        given(assistantAnswerPort.ask(command.question())).willReturn(answer);

        AssistantAnswerResult result = assistantChatService.ask(command);

        assertThat(result.answer()).isEqualTo("이메일로 가입할 수 있습니다.");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().get(0).title()).isEqualTo("회원가입 방법");
        assertThat(result.sources().get(0).sourceType()).isEqualTo("FAQ");

        ArgumentCaptor<AssistantChatLog> logCaptor =
                ArgumentCaptor.forClass(AssistantChatLog.class);
        verify(chatLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getQuestion()).isEqualTo(command.question());
        assertThat(logCaptor.getValue().getAnswer()).isEqualTo(result.answer());
        assertThat(logCaptor.getValue().getMatchedDocumentIds()).containsExactly(documentId);
        assertThat(logCaptor.getValue().getSimilarityScores()).containsExactly(0.9f);
    }

    @Test
    @DisplayName("검색된 문서가 없으면 sources는 빈 리스트를 반환한다")
    void ask_whenNoDocumentsRetrieved_returnsEmptySources() {
        AskAssistantCommand command =
                new AskAssistantCommand(UUID.randomUUID(), "매칭 성공 확률은 몇 %인가요?");
        given(assistantAnswerPort.ask(command.question()))
                .willReturn(new AssistantAnswer("확인이 어렵습니다.", List.of()));

        AssistantAnswerResult result = assistantChatService.ask(command);

        assertThat(result.answer()).isEqualTo("확인이 어렵습니다.");
        assertThat(result.sources()).isEmpty();
    }

    @Test
    @DisplayName("포트에서 LlmResponseFailedException이 발생하면 그대로 전파한다")
    void ask_whenPortThrows_propagatesException() {
        AskAssistantCommand command = new AskAssistantCommand(UUID.randomUUID(), "질문");
        given(assistantAnswerPort.ask(command.question()))
                .willThrow(new LlmResponseFailedException());

        assertThatThrownBy(() -> assistantChatService.ask(command))
                .isInstanceOf(LlmResponseFailedException.class);
    }

    @Test
    @DisplayName("채팅 로그 저장이 실패해도 이미 생성된 답변은 정상 반환한다")
    void ask_whenChatLogSaveThrows_stillReturnsAnswer() {
        AskAssistantCommand command = new AskAssistantCommand(UUID.randomUUID(), "질문");
        given(assistantAnswerPort.ask(command.question()))
                .willReturn(new AssistantAnswer("정상 답변입니다.", List.of()));
        given(chatLogRepository.save(any())).willThrow(new RuntimeException("DB 연결 실패"));

        AssistantAnswerResult result = assistantChatService.ask(command);

        assertThat(result.answer()).isEqualTo("정상 답변입니다.");
        assertThat(result.sources()).isEmpty();
    }
}
