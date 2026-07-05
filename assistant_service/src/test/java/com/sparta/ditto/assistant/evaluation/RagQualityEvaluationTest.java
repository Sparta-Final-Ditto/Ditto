package com.sparta.ditto.assistant.evaluation;

import com.sparta.ditto.assistant.application.dto.command.AskAssistantCommand;
import com.sparta.ditto.assistant.application.dto.result.AssistantAnswerResult;
import com.sparta.ditto.assistant.application.service.AssistantChatService;
import com.sparta.ditto.assistant.infrastructure.document.AssistantDocumentChunker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * 골든셋(evaluation/golden-set.md) 기반 RAG 품질 평가
 * 리포트를 {@code build/rag-evaluation-reports/rag-evaluation-<provider>-<시각>.md} 저장
 */
@SpringBootTest
@Tag("evaluation")
class RagQualityEvaluationTest {

    private static final String GOLDEN_SET_RESOURCE_PATH = "evaluation/golden-set.md";
    private static final Path REPORT_OUTPUT_DIR = Path.of("build", "rag-evaluation-reports");
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private AssistantChatService assistantChatService;

    @Autowired
    private OllamaChatModel ollamaChatModel;

    @Autowired
    private OpenAiChatModel openAiChatModel;

    @Value("${assistant.llm.provider}")
    private String llmProvider;

    @Value("${spring.ai.ollama.embedding.model}")
    private String embeddingModel;

    @Value("${spring.ai.ollama.chat.model}")
    private String ollamaChatModelName;

    @Value("${spring.ai.openai.chat.options.model}")
    private String apiChatModelName;

    @Value("${assistant.rag.search.top-k}")
    private int searchTopK;

    @Value("${assistant.rag.search.similarity-threshold}")
    private double searchSimilarityThreshold;

    @Test
    @DisplayName("골든셋 30문항의 Hit@K(검색)와 Faithfulness(생성)를 측정해 리포트를 출력·저장한다")
    void evaluateRagQuality() throws IOException {
        List<GoldenSetItem> goldenSet = loadGoldenSet();
        System.out.println(environmentSummary());
        ChatClient judgeChatClient = ChatClient.builder(judgeChatModel()).build();

        List<EvaluationOutcome> outcomes = new ArrayList<>();
        for (GoldenSetItem item : goldenSet) {
            outcomes.add(evaluate(item, judgeChatClient));
        }

        String report = buildReport(outcomes);
        System.out.println(report);
        Path savedPath = saveReportToFile(report);
        System.out.printf("리포트 저장 위치: %s%n", savedPath.toAbsolutePath());
    }

    /** 채점 모델은 항상 실제 답변을 생성한 provider와 동일한 모델을 쓴다 (advisor 없는 순수 ChatClient). */
    private ChatModel judgeChatModel() {
        return "api".equals(llmProvider) ? openAiChatModel : ollamaChatModel;
    }

    /** 생성(및 judge)에 실제로 쓰이는 모델명 — provider 스위치에 따라 ollama/api 중 하나. */
    private String generationModelName() {
        return "api".equals(llmProvider) ? apiChatModelName : ollamaChatModelName;
    }

    private String environmentSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== 실행 환경 =====\n");
        sb.append("임베딩: ollama (%s) — provider 스위치와 무관하게 항상 고정%n".formatted(embeddingModel));
        sb.append("답변 생성: %s (%s)%n".formatted(llmProvider, generationModelName()));
        sb.append("채점(judge): %s (%s) — 답변 생성과 동일 provider 사용%n"
                .formatted(llmProvider, generationModelName()));
        sb.append("=====================");
        return sb.toString();
    }

    private List<GoldenSetItem> loadGoldenSet() throws IOException {
        String markdown = StreamUtils.copyToString(
                new ClassPathResource(GOLDEN_SET_RESOURCE_PATH).getInputStream(),
                StandardCharsets.UTF_8);
        return GoldenSetLoader.parse(markdown);
    }

    private EvaluationOutcome evaluate(GoldenSetItem item, ChatClient judgeChatClient) {
        List<Document> retrieved = vectorStore.similaritySearch(SearchRequest.builder()
                .query(item.question())
                .topK(searchTopK)
                .similarityThreshold(searchSimilarityThreshold)
                .build());

        int rank = rankOf(item, retrieved);

        AssistantAnswerResult answerResult =
                assistantChatService.ask(new AskAssistantCommand(null, item.question()));
        JudgeVerdict verdict = judge(judgeChatClient, item, answerResult.answer(), retrieved);

        return new EvaluationOutcome(item, retrieved.size(), rank, verdict);
    }

    /** 기대 문서의 top-K 내 순위(1-based)를 반환한다. trap 문항은 -1, top-K 밖이면 0. */
    private int rankOf(GoldenSetItem item, List<Document> retrieved) {
        if (item.isTrap()) {
            return -1;
        }
        String expectedDocumentId =
                AssistantDocumentChunker.toDocumentId(item.expectedDocumentId());
        for (int i = 0; i < retrieved.size(); i++) {
            if (expectedDocumentId.equals(retrieved.get(i).getId())) {
                return i + 1;
            }
        }
        return 0;
    }

    private JudgeVerdict judge(
            ChatClient judgeChatClient,
            GoldenSetItem item,
            String answer,
            List<Document> retrieved) {
        String context = retrieved.isEmpty()
                ? "(검색된 문서 없음)"
                : retrieved.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

        String judgePrompt = """
                당신은 RAG 챗봇의 답변 품질을 평가하는 채점자입니다.
                아래 [참고 문서]에 있는 내용만을 근거로 [챗봇 답변]이 작성되었는지 1~5점으로 평가하세요.

                [질문]
                %s

                [참고 문서]
                %s

                [챗봇 답변]
                %s

                평가 기준:
                - 5점: 참고 문서 내용과 완전히 일치하며 근거 없는 내용이 전혀 없다.
                - 3점: 대체로 근거가 있으나 일부 불확실하거나 과장된 표현이 있다.
                - 1점: 참고 문서에 없는 내용을 지어냈다(환각).
                - 참고 문서가 없는데 챗봇이 "확인이 어렵다"거나 서비스와 무관하다고 정직하게 안내했다면 5점을 준다.

                반드시 아래 형식으로만 답하세요. 다른 말은 절대 덧붙이지 마세요.
                SCORE: <1~5 숫자>
                REASON: <한 문장 이유>
                """.formatted(item.question(), context, answer);

        String judgeResponse = judgeChatClient.prompt().user(judgePrompt).call().content();
        return JudgeVerdict.parse(judgeResponse);
    }

    private String buildReport(List<EvaluationOutcome> outcomes) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n===== RAG 골든셋 평가 리포트 =====\n");
        sb.append(environmentSummary()).append('\n');
        sb.append("%-14s %-18s %-6s %-6s %s%n"
                .formatted("id", "category", "rank", "score", "judge reason"));

        long expectedCount = 0;
        long hitCount = 0;
        long trapCount = 0;
        long trapLeakCount = 0;
        double scoreSum = 0;

        for (EvaluationOutcome outcome : outcomes) {
            GoldenSetItem item = outcome.item();
            boolean isTrap = item.isTrap();
            boolean hit = !isTrap && outcome.rank() > 0;

            if (isTrap) {
                trapCount++;
                if (outcome.retrievedCount() > 0) {
                    trapLeakCount++;
                }
            } else {
                expectedCount++;
                if (hit) {
                    hitCount++;
                }
            }
            scoreSum += outcome.verdict().score();

            String rankColumn = isTrap
                    ? (outcome.retrievedCount() > 0 ? "LEAK" : "OK")
                    : (hit ? String.valueOf(outcome.rank()) : "MISS");
            sb.append("%-14s %-18s %-6s %-6d %s%n".formatted(
                    item.id(), item.category(), rankColumn,
                    outcome.verdict().score(), outcome.verdict().reason()));
        }

        sb.append('\n');
        sb.append("Hit@%d: %d/%d (%.1f%%)%n".formatted(
                searchTopK, hitCount, expectedCount,
                expectedCount == 0 ? 0 : hitCount * 100.0 / expectedCount));
        sb.append("Trap leak (정답 없어야 하는데 문서가 검색된 건수): %d/%d%n"
                .formatted(trapLeakCount, trapCount));
        sb.append("평균 Faithfulness: %.2f / 5%n"
                .formatted(outcomes.isEmpty() ? 0 : scoreSum / outcomes.size()));
        sb.append("=================================");
        return sb.toString();
    }

    private Path saveReportToFile(String report) throws IOException {
        Files.createDirectories(REPORT_OUTPUT_DIR);
        String timestamp = LocalDateTime.now().format(REPORT_TIMESTAMP_FORMAT);
        Path file = REPORT_OUTPUT_DIR.resolve(
                "rag-evaluation-%s-%s.md".formatted(llmProvider, timestamp));
        Files.writeString(file, report, StandardCharsets.UTF_8);
        return file;
    }

    private record EvaluationOutcome(
            GoldenSetItem item, int retrievedCount, int rank, JudgeVerdict verdict) {
    }
}
