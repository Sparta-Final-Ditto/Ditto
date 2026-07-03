package com.sparta.ditto.match.infrastructure.Llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OllamaAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private OllamaAdapter ollamaAdapter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ollamaAdapter = new OllamaAdapter(chatClient);
    }

    @Test
    @DisplayName("정상 응답 시 LLM이 생성한 텍스트를 반환한다")
    void generate_success_returnsContent() {
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.user("공통 관심사 설명해줘")).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);
        given(callResponseSpec.content()).willReturn("두 분 다 여행을 좋아하시네요!");

        String result = ollamaAdapter.generate("공통 관심사 설명해줘");

        assertThat(result).isEqualTo("두 분 다 여행을 좋아하시네요!");
    }

    @Test
    @DisplayName("LLM 호출 실패 시 RuntimeException으로 감싸서 던진다")
    void generate_llmFails_throwsRuntimeException() {
        given(chatClient.prompt()).willThrow(new RuntimeException("connection timeout"));

        assertThatThrownBy(() -> ollamaAdapter.generate("prompt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Ollama 호출 실패");
    }
}