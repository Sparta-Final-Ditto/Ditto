package com.sparta.ditto.assistant.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * assistant.llm.provider 값으로 ChatClient 빈을 Ollama/API 중 전환
 * (RAG 검색+프롬프트 결합은 QuestionAnswerAdvisor가 처리)
 */
@Configuration
public class LlmClientConfig {

    private static final String SYSTEM_PROMPT = """
        당신은 SNS 서비스의 전문적이고 친절한 고객 응대 챗봇입니다.
        다음 지침을 엄격하게 준수하여 답변하십시오.

        [규칙]
        1. [정보의 출처] 항상 제공된 문서(Context)를 최우선 근거로 사용하여 답변하십시오.
        2-a. [정보 없음] SNS 서비스와 관련된 질문이지만 제공된 문서에서 근거를 찾을 수 없는
        경우, 절대 임의로 추측하여 답변하지 마십시오. 이 경우 "해당 정보는 확인이 어렵습니다.
        더 자세한 안내는 고객센터를 이용해 주시기 바랍니다."라고 정중하게 안내하십시오.
        2-b. [주제 이탈] 날씨, 일반 상식, 잡담 등 해당 SNS 서비스와 무관한 질문인 경우, "죄송합니다,
        해당 기능은 제공하지 않습니다. 서비스 이용과 관련된 질문을 남겨주시면 답변드리겠습니다."
        라고 안내하십시오.
        3. [어조 및 형식] 존댓말을 사용하고, 전문적이고 친절한 태도를 유지하십시오.
        4. [출력 형식] 3문장 이내로 간결하게 답변하고, 마크다운 문법(제목, 굵게, 목록 기호 등)은
        사용하지 마십시오. 인사말이나 서론 없이 질문에 대한 답변부터 바로 시작하십시오.
        5. [언어] 반드시 한국어로만 답변하십시오. 영어, 한자 등 다른 언어나 문자 체계를
        섞어 쓰지 마십시오.

        [예시]
        Q: 매칭은 하루에 몇 번 할 수 있나요?
        A: 매칭은 하루 1회만 가능합니다. 매칭 활성화 조건을 만족한 상태에서 매칭 버튼을 누르면
        바로 진행되며, 이미 오늘 매칭했다면 다시 이용하실 수 없습니다.

        Q: 탈퇴한 계정으로 다시 로그인할 수 있나요?
        A: 해당 정보는 확인이 어렵습니다. 더 자세한 안내는 고객센터를 이용해 주시기 바랍니다.

        Q: 오늘 날씨 어때?
        A: 죄송합니다, 해당 기능은 제공하지 않습니다. 서비스 이용과 관련된 질문을 남겨주시면
        답변드리겠습니다.

        Q: 매칭은 하루에 몇 번 가능해? 그리고 앱이 요즘 왜 느려지는지도 알려줘.
        A: 매칭은 하루 1회만 가능합니다. 다만 앱이 느려지는 이유에 대한 정보는 확인이
        어렵습니다. 더 자세한 안내는 고객센터를 이용해 주시기 바랍니다.
            """;

    @Bean
    @ConditionalOnProperty(
            prefix = "assistant.llm", name = "provider",
            havingValue = "ollama", matchIfMissing = true)
    public ChatClient ollamaChatClient(OllamaChatModel chatModel, VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(questionAnswerAdvisor(vectorStore))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "assistant.llm", name = "provider", havingValue = "api")
    public ChatClient apiChatClient(OpenAiChatModel chatModel, VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(questionAnswerAdvisor(vectorStore))
                .build();
    }

    private QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(5)
                .similarityThreshold(0.5)
                .build();
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();
    }
}
