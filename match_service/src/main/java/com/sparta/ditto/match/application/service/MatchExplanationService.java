package com.sparta.ditto.match.application.service;

import com.sparta.ditto.match.application.dto.EmbedTextRequestDto;
import com.sparta.ditto.match.application.dto.EmbedTextResponseDto;
import com.sparta.ditto.match.application.port.ExplanationCachePort;
import com.sparta.ditto.match.application.port.LlmPort;
import com.sparta.ditto.match.domain.entity.ExplanationExample;
import com.sparta.ditto.match.domain.entity.MatchingExplanation;
import com.sparta.ditto.match.domain.repository.ExplanationExampleRepository;
import com.sparta.ditto.match.domain.repository.MatchingExplanationRepository;
import com.sparta.ditto.match.infrastructure.feign.EmbeddingServiceClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchExplanationService {

    private static final int RETRIEVAL_TOP_K = 3;
    private static final double RETRIEVAL_SIMILARITY_THRESHOLD = 0.5;
    private static final String SOURCE_TYPE_GENERATED = "GENERATED";

    private final LlmPort llmPort;
    private final ExplanationCachePort explanationCachePort;
    private final MatchingExplanationRepository explanationRepository;
    private final ExplanationExampleRepository explanationExampleRepository;
    private final EmbeddingServiceClient embeddingServiceClient;

    /**
     * RAG 매칭 설명 생성
     * 1. Redis 캐시 확인
     * 2. DB 확인
     * 3. embedding_service로 쿼리 텍스트 임베딩 → pgvector HNSW 검색 (비슷한 태그 조합의 과거 사례)
     * 4. 검색된 사례를 few-shot 예시로 프롬프트에 주입 → LLM 호출 (Fallback 포함)
     * 5. Redis + DB 저장, (LLM 생성 성공 시) embedding_service로 재임베딩 후 explanation_examples에 적재
     */
    public String generateExplanation(UUID userId, UUID matchedUserId,
                                      List<String> commonTags, float score) {

        // 1. Redis 캐시 확인
        Optional<String> cached = explanationCachePort.getExplanation(userId, matchedUserId);
        if (cached.isPresent()) {
            log.info("[Explanation] Redis 캐시 히트 userId={}", userId);
            return cached.get();
        }

        // 2. DB 확인 (이전에 생성된 적 있으면 재사용)
        Optional<MatchingExplanation> saved = explanationRepository
                .findByUserIdAndMatchedUserId(userId, matchedUserId);
        if (saved.isPresent()) {
            String explanation = saved.get().getExplanationText();
            explanationCachePort.saveExplanation(userId, matchedUserId, explanation);
            log.info("[Explanation] DB 히트 userId={}", userId);
            return explanation;
        }

        // 3. 벡터 검색 - 비슷한 태그 조합의 과거 사례(RAG 검색 단계)
        List<String> examples = retrieveSimilarExamples(commonTags, score);
        log.info("[Explanation] RAG 검색 결과 {}건 userId={}", examples.size(), userId);

        // 4. LLM 호출 (검색된 사례를 컨텍스트로 주입, 실패 시 Fallback)
        String explanation;
        boolean generatedByLlm;
        try {
            String prompt = buildPrompt(commonTags, score, examples);
            explanation = llmPort.generate(prompt);
            generatedByLlm = true;
            log.info("[Explanation] LLM 생성 완료 userId={}", userId);
        } catch (Exception e) {
            log.warn("[Explanation] LLM 호출 실패, fallback 사용 userId={} error={}",
                    userId, e.getMessage());
            explanation = buildFallback(commonTags, score);
            generatedByLlm = false;
        }

        // 5. Redis + DB 저장
        explanationCachePort.saveExplanation(userId, matchedUserId, explanation);
        explanationRepository.save(MatchingExplanation.of(userId, matchedUserId, explanation));

        // LLM이 실제로 생성한 결과만 벡터스토어에 재적재 (fallback 정형문은 학습 가치가 없어 제외)
        if (generatedByLlm) {
            indexGeneratedExplanation(userId, matchedUserId, commonTags, score, explanation);
        }

        return explanation;
    }

    /**
     * RAG 검색 단계 - embedding_service로 쿼리 텍스트를 임베딩하고 pgvector로 유사 사례 검색
     */
    private List<String> retrieveSimilarExamples(List<String> commonTags, float score) {
        try {
            String queryText = buildRetrievalQuery(commonTags, score);
            float[] queryVector = embedText(queryText);
            String queryVectorStr = floatArrayToVectorString(queryVector);

            List<Object[]> rows = explanationExampleRepository.findSimilarExamples(
                    queryVectorStr, RETRIEVAL_SIMILARITY_THRESHOLD, RETRIEVAL_TOP_K);

            return rows.stream()
                    .map(row -> (String) row[1])  // [id, content, similarity] 중 content
                    .toList();
        } catch (Exception e) {
            // embedding_service/DB 장애가 설명 생성 전체를 막으면 안 됨 → 빈 결과로 계속 진행
            log.warn("[Explanation] RAG 검색 실패, 기본 예시로 대체 error={}", e.getMessage());
            return List.of();
        }
    }

    private String buildRetrievalQuery(List<String> commonTags, float score) {
        String tagStr = commonTags.isEmpty() ? "공통 관심사 없음" : String.join(",", commonTags);
        int scorePercent = (int) (score * 100);
        return "공통태그=%s / 점수=%d%%".formatted(tagStr, scorePercent);
    }

    /**
     * RAG 프롬프트 - 벡터 검색으로 찾은 사례를 few-shot 예시로 동적 주입
     */
    private String buildPrompt(List<String> commonTags, float score, List<String> examples) {
        String tagStr = commonTags.isEmpty() ? "공통 관심사 없음" : String.join(", ", commonTags);
        int scorePercent = (int) (score * 100);
        String exampleSection = formatExamples(examples);

        return """
                당신은 SNS 매칭 서비스의 친근한 큐레이터입니다.
                두 사용자의 공통 관심사를 보고 왜 잘 맞는지 한국어로 설명해주세요.
                
                [규칙]
                - 반드시 한국어로 답변
                - 2문장 이내, 100자 이내
                - 따뜻하고 긍정적인 톤
                - 마크다운 금지
                - 아래 예시는 참고용 스타일 가이드일 뿐, 그대로 베끼지 말고 입력값에 맞게 새로 작성할 것
                
                [유사 사례]
                %s
                
                [입력]
                공통태그: %s
                매칭점수: %d%%
                
                [답변]
                """.formatted(exampleSection, tagStr, scorePercent);
    }

    private String formatExamples(List<String> examples) {
        if (examples.isEmpty()) {
            // 벡터스토어 검색 결과가 없을 때(장애 등 극단적 콜드 스타트)의 최소 안전장치
            return "Q: 공통태그=여행,사진 / 점수=82%\nA: 여행과 사진을 함께 즐기는 감성이 딱 맞는 두 분이에요! "
                    + "서로의 여행 사진 이야기로 대화가 끊이지 않을 것 같아요.";
        }
        return String.join("\n\n", examples);
    }

    /**
     * RAG 적재 단계 - embedding_service로 재임베딩 후 explanation_examples에 저장
     * userId+matchedUserId 조합으로 결정론적 ID를 만들어, 같은 쌍에 대한 재생성 시 upsert되게 함
     */
    private void indexGeneratedExplanation(UUID userId, UUID matchedUserId,
                                           List<String> commonTags, float score,
                                           String explanation) {
        try {
            String tagStr = commonTags.isEmpty() ? "공통 관심사 없음" : String.join(",", commonTags);
            int scorePercent = (int) (score * 100);
            String content = "Q: 공통태그=%s / 점수=%d%%\nA: %s"
                    .formatted(tagStr, scorePercent, explanation);

            float[] vector = embedText(content);

            UUID documentId = UUID.nameUUIDFromBytes(
                    ("explanation:" + userId + ":" + matchedUserId).getBytes(StandardCharsets.UTF_8)
            );

            explanationExampleRepository.save(ExplanationExample.of(
                    documentId, content, vector, tagStr, scorePercent, SOURCE_TYPE_GENERATED));
        } catch (Exception e) {
            // 벡터스토어 적재 실패는 설명 생성 자체를 실패시키면 안 되는 부수 효과이므로 로그만 남김
            log.warn("[Explanation] RAG 적재 실패 userId={} error={}", userId, e.getMessage());
        }
    }

    /**
     * embedding_service 호출 - 임의 텍스트를 벡터로 변환 (match_service는 임베딩을 직접 계산하지 않음)
     */
    private float[] embedText(String text) {
        EmbedTextResponseDto response = embeddingServiceClient
                .embedText(new EmbedTextRequestDto(text))
                .getData();

        List<Float> vectorList = response.vector();
        float[] vector = new float[vectorList.size()];
        for (int i = 0; i < vectorList.size(); i++) {
            vector[i] = vectorList.get(i);
        }
        return vector;
    }

    private String floatArrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * LLM 실패 시 규칙 기반 Fallback
     */
    private String buildFallback(List<String> commonTags, float score) {
        int scorePercent = (int) (score * 100);
        if (commonTags.isEmpty()) {
            return String.format("매칭 점수 %d%%로 서로 잘 맞는 분들이에요! 새로운 인연을 만들어보세요.", scorePercent);
        }
        String tag1 = commonTags.get(0);
        if (commonTags.size() == 1) {
            return String.format("%s에 대한 공통 관심사로 연결된 두 분이에요. 매칭 점수 %d%%!", tag1, scorePercent);
        }
        return String.format("%s, %s 등 %d개의 공통 관심사를 가진 두 분이에요! 매칭 점수 %d%%로 잘 맞을 것 같아요.",
                tag1, commonTags.get(1), commonTags.size(), scorePercent);
    }
}
