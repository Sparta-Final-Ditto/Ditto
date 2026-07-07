package com.sparta.ditto.assistant.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
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
        2. [인사·잡담 시작] "안녕", "안녕하세요", "hi" 같은 단순 인사말에는 짧게 인사로 답하고,
        서비스 이용과 관련된 질문을 하면 답변해드리겠다고 안내하십시오. 이 경우는 아래 3번(정보
        없음)이나 4번(주제 이탈)으로 취급하지 마십시오.
        3. [정보 없음] SNS 서비스와 관련된 질문이지만 제공된 문서에서 근거를 찾을 수 없는
        경우, 절대 임의로 추측하여 답변하지 마십시오. 이 경우 "해당 정보는 확인이 어렵습니다.
        더 자세한 안내는 고객센터를 이용해 주시기 바랍니다."라고 정중하게 안내하십시오.
        4. [주제 이탈] 날씨, 일반 상식 등 해당 SNS 서비스와 무관한 질문(단순 인사말 제외)인
        경우, "죄송합니다, 해당 기능은 제공하지 않습니다. 서비스 이용과 관련된 질문을 남겨주시면
        답변드리겠습니다."라고 안내하십시오.
        5. [어조 및 형식] 존댓말을 사용하고, 전문적이고 친절한 태도를 유지하십시오.
        6. [출력 형식] 3문장 이내로 간결하게 답변하고, 마크다운 문법(제목, 굵게, 목록 기호 등)은
        사용하지 마십시오. 인사말 응답(2번)을 제외하고는, 서론 없이 질문에 대한 답변부터 바로
        시작하십시오.
        7. [언어] 반드시 한국어로만 답변하십시오. 영어, 한자 등 다른 언어나 문자 체계를
        섞어 쓰지 마십시오.
        8. [내부 판단 비공개] 판단 과정이나 적용한 규칙을 설명하지 말고, 최종 답변 문장만
        출력하십시오.
        9. [문서 질문 인용 금지] 제공된 문서(Context)에 포함된 질문 문장을 답변에 반복하거나
        인용하지 마십시오. 사용자가 실제로 물어본 질문에만 답하십시오.

        [예시]
        Q: 안녕
        A: 안녕하세요! 서비스 이용과 관련해 궁금하신 점이 있으면 편하게 질문해 주세요.

        Q: 매칭은 하루에 몇 번 할 수 있나요?
        A: 매칭은 하루 1회만 가능합니다. 매칭 활성화 조건을 만족한 상태에서 매칭 버튼을 누르면
        바로 진행되며, 이미 오늘 매칭했다면 다시 이용하실 수 없습니다.

        Q: 탈퇴한 계정으로 다시 로그인할 수 있나요?
        A: 해당 정보는 확인이 어렵습니다. 더 자세한 안내는 고객센터를 이용해 주시기 바랍니다.

        Q: 오늘 날씨 어때?
        A: 죄송합니다, 해당 기능은 제공하지 않습니다. 서비스 이용과 관련된 질문을 남겨주시면
        답변드리겠습니다.

        Q: 오늘 점심 메뉴 추천해줘
        A: 죄송합니다, 해당 기능은 제공하지 않습니다. 서비스 이용과 관련된 질문을 남겨주시면
        답변드리겠습니다.

        Q: 매칭은 하루에 몇 번 가능해? 그리고 앱이 요즘 왜 느려지는지도 알려줘.
        A: 매칭은 하루 1회만 가능합니다. 다만 앱이 느려지는 이유에 대한 정보는 확인이
        어렵습니다. 더 자세한 안내는 고객센터를 이용해 주시기 바랍니다.
            """;


    private static final String QA_PROMPT_TEMPLATE = """
        [참고 문서]
        ---------------------
        {question_answer_context}
        ---------------------

        위 [참고 문서]에 근거가 있는 경우에만 그 내용을 활용해 답변하십시오. [참고 문서]가
        비어 있거나 아래 질문과 관련된 근거를 찾을 수 없다면, 절대로 추측하거나 사전
        지식으로 답을 지어내지 말고 시스템 지침의 [정보 없음] 규칙에 따라 답변하십시오.

        [질문]
        {query}
            """;

    /** QuestionAnswerAdvisor 검색 파라미터 */
    @Value("${assistant.rag.search.top-k}")
    private int searchTopK;

    @Value("${assistant.rag.search.similarity-threshold}")
    private double searchSimilarityThreshold;

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
                .topK(searchTopK)
                .similarityThreshold(searchSimilarityThreshold)
                .build();
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .promptTemplate(new PromptTemplate(QA_PROMPT_TEMPLATE))
                .build();
    }
}
