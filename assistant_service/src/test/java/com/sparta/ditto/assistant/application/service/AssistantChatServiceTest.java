package com.sparta.ditto.assistant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.assistant.application.dto.command.AskAssistantCommand;
import com.sparta.ditto.assistant.application.dto.result.AssistantAnswerResult;
import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import com.sparta.ditto.assistant.domain.exception.LlmResponseFailedException;
import com.sparta.ditto.assistant.domain.repository.AssistantChatLogRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssistantChatServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private AssistantChatLogRepository chatLogRepository;

    @InjectMocks
    private AssistantChatService assistantChatService;

    @Test
    @DisplayName("ask() 성공 시 검색된 문서를 출처로 채워 답변을 반환하고 로그를 저장한다")
    void ask_success_returnsAnswerWithSourcesAndSavesLog() {
        UUID userId = UUID.randomUUID();
        AskAssistantCommand command = new AskAssistantCommand(userId, "회원가입은 어떻게 하나요?");

        Document document = Document.builder()
                .id(UUID.randomUUID().toString())
                .text("Q: 회원가입은 어떻게 하나요?\nA: 이메일로 가입합니다.")
                .metadata(Map.of("title", "회원가입 방법", "sourceType", "FAQ"))
                .score(0.9)
                .build();
        ChatClientResponse chatClientResponse =
                buildChatClientResponse("이메일로 가입할 수 있습니다.", List.of(document));

        given(chatClient.prompt().user(command.question()).call().chatClientResponse())
                .willReturn(chatClientResponse);

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
        assertThat(logCaptor.getValue().getMatchedDocumentIds())
                .containsExactly(UUID.fromString(document.getId()));
        assertThat(logCaptor.getValue().getSimilarityScores()).containsExactly(0.9f);
    }

    @Test
    @DisplayName("검색된 문서가 없으면 sources는 빈 리스트를 반환한다")
    void ask_whenNoDocumentsRetrieved_returnsEmptySources() {
        AskAssistantCommand command =
                new AskAssistantCommand(UUID.randomUUID(), "매칭 성공 확률은 몇 %인가요?");
        ChatClientResponse chatClientResponse = buildChatClientResponse("확인이 어렵습니다.", List.of());

        given(chatClient.prompt().user(command.question()).call().chatClientResponse())
                .willReturn(chatClientResponse);

        AssistantAnswerResult result = assistantChatService.ask(command);

        assertThat(result.answer()).isEqualTo("확인이 어렵습니다.");
        assertThat(result.sources()).isEmpty();
    }

    @Test
    @DisplayName("ChatClient 호출이 실패하면 LlmResponseFailedException을 던진다")
    void ask_whenChatClientThrows_throwsLlmResponseFailedException() {
        AskAssistantCommand command = new AskAssistantCommand(UUID.randomUUID(), "질문");

        given(chatClient.prompt().user(command.question()).call().chatClientResponse())
                .willThrow(new RuntimeException("Ollama 연결 실패"));

        assertThatThrownBy(() -> assistantChatService.ask(command))
                .isInstanceOf(LlmResponseFailedException.class);
    }

    private ChatClientResponse buildChatClientResponse(
            String answer, List<Document> retrievedDocuments) {
        Generation generation = new Generation(new AssistantMessage(answer));
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        Map<String, Object> context =
                Map.of(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, retrievedDocuments);
        return new ChatClientResponse(chatResponse, context);
    }
}
