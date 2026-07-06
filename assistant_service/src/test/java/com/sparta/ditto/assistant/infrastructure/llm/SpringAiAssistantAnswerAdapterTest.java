package com.sparta.ditto.assistant.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.sparta.ditto.assistant.application.port.AssistantAnswerPort.AssistantAnswer;
import com.sparta.ditto.assistant.application.port.AssistantAnswerPort.RetrievedDocument;
import com.sparta.ditto.assistant.domain.exception.LlmResponseFailedException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;

@ExtendWith(MockitoExtension.class)
class SpringAiAssistantAnswerAdapterTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @InjectMocks
    private SpringAiAssistantAnswerAdapter adapter;

    @Test
    @DisplayName("ask()는 답변 텍스트와 근거 문서를 RetrievedDocument로 매핑해 반환한다")
    void ask_success_mapsRetrievedDocuments() {
        String question = "회원가입은 어떻게 하나요?";
        UUID documentId = UUID.randomUUID();
        Document document = Document.builder()
                .id(documentId.toString())
                .text("Q: 회원가입은 어떻게 하나요?\nA: 이메일로 가입합니다.")
                .metadata(Map.of("title", "회원가입 방법", "sourceType", "FAQ"))
                .score(0.9)
                .build();
        given(chatClient.prompt().user(question).call().chatClientResponse())
                .willReturn(buildChatClientResponse("이메일로 가입할 수 있습니다.", List.of(document)));

        AssistantAnswer answer = adapter.ask(question);

        assertThat(answer.answerText()).isEqualTo("이메일로 가입할 수 있습니다.");
        assertThat(answer.retrievedDocuments()).containsExactly(
                new RetrievedDocument(documentId, "회원가입 방법", "FAQ", 0.9f));
    }

    @Test
    @DisplayName("검색된 문서가 없으면 빈 리스트를 반환한다")
    void ask_whenNoDocumentsRetrieved_returnsEmptyList() {
        String question = "매칭 성공 확률은 몇 %인가요?";
        given(chatClient.prompt().user(question).call().chatClientResponse())
                .willReturn(buildChatClientResponse("확인이 어렵습니다.", List.of()));

        AssistantAnswer answer = adapter.ask(question);

        assertThat(answer.answerText()).isEqualTo("확인이 어렵습니다.");
        assertThat(answer.retrievedDocuments()).isEmpty();
    }

    @Test
    @DisplayName("ChatClient 호출이 실패하면 LlmResponseFailedException을 던진다")
    void ask_whenChatClientThrows_throwsLlmResponseFailedException() {
        String question = "질문";
        given(chatClient.prompt().user(question).call().chatClientResponse())
                .willThrow(new RuntimeException("Ollama 연결 실패"));

        assertThatThrownBy(() -> adapter.ask(question))
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
