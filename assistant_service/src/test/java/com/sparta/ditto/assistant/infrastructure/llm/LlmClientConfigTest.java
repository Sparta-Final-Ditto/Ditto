package com.sparta.ditto.assistant.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class LlmClientConfigTest {

    private final LlmClientConfig llmClientConfig = new LlmClientConfig();

    @Mock
    private OllamaChatModel ollamaChatModel;

    @Mock
    private OpenAiChatModel openAiChatModel;

    @Mock
    private VectorStore vectorStore;

    @Test
    @DisplayName("ollamaChatClient()는 ChatClient 빈을 생성한다")
    void ollamaChatClient_createsChatClient() {
        ChatClient chatClient = llmClientConfig.ollamaChatClient(ollamaChatModel, vectorStore);

        assertThat(chatClient).isNotNull();
    }

    @Test
    @DisplayName("apiChatClient()는 ChatClient 빈을 생성한다")
    void apiChatClient_createsChatClient() {
        ChatClient chatClient = llmClientConfig.apiChatClient(openAiChatModel, vectorStore);

        assertThat(chatClient).isNotNull();
    }
}
