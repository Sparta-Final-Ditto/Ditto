package com.sparta.ditto.assistant.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** assistant.llm.provider 값으로 ChatClient 빈을 Ollama/API 중 전환 (RAG 검색+프롬프트 결합은 QuestionAnswerAdvisor가 처리) */
@Configuration
public class LlmClientConfig {

    private static final String SYSTEM_PROMPT = """
        당신은 매칭 SNS 서비스의 전문적이고 친절한 고객 응대 챗봇입니다.
        다음 지침을 엄격하게 준수하여 답변하십시오.

        1. [정보의 출처] 항상 제공된 문서(Context)를 최우선 근거로 사용하여 답변하십시오.
        2. [예외 처리] 문서에서 근거를 찾을 수 없는 내용이거나, 매칭 SNS 서비스와 무관한 질문인 경우 절대 임의로 추측하여 답변하지 마십시오. 이 경우 "해당 정보는 확인이 어렵습니다. 더 자세한 안내는 고객센터를 이용해 주시기 바랍니다."라고 정중하게 안내하십시오.
        3. [어조 및 형식] 존댓말을 사용하고, 전문적이고 친절한 태도를 유지하십시오. 가독성을 높이기 위해 필요한 경우 간결한 문장과 목록 기호를 사용하십시오.
            """;

    @Bean
    @ConditionalOnProperty(prefix = "assistant.llm", name = "provider", havingValue = "ollama", matchIfMissing = true)
    public ChatClient ollamaChatClient(OllamaChatModel chatModel, VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "assistant.llm", name = "provider", havingValue = "api")
    public ChatClient apiChatClient(OpenAiChatModel chatModel, VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
                .build();
    }
}
