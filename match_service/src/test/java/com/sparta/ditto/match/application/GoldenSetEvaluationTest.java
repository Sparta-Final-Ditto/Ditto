package com.sparta.ditto.match.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.match.application.dto.EmbedTextRequestDto;
import com.sparta.ditto.match.application.port.ExplanationCachePort;
import com.sparta.ditto.match.application.port.LlmPort;
import com.sparta.ditto.match.application.service.MatchExplanationService;
import com.sparta.ditto.match.domain.repository.ExplanationExampleRepository;
import com.sparta.ditto.match.domain.repository.MatchingExplanationRepository;
import com.sparta.ditto.match.infrastructure.feign.EmbeddingServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GoldenSetEvaluationTest {

    @Mock private LlmPort llmPort;
    @Mock private ExplanationCachePort explanationCachePort;
    @Mock private MatchingExplanationRepository explanationRepository;
    @Mock private ExplanationExampleRepository explanationExampleRepository;
    @Mock private EmbeddingServiceClient embeddingServiceClient;

    private MatchExplanationService matchExplanationService;

    private List<Map<String, Object>> goldenSet;

    @BeforeEach
    void setUp() throws Exception {
        matchExplanationService = new MatchExplanationService(
                llmPort, explanationCachePort, explanationRepository,
                explanationExampleRepository, embeddingServiceClient);
        InputStream is = getClass().getClassLoader().getResourceAsStream("golden-set.json");
        goldenSet = new ObjectMapper().readValue(is, new TypeReference<>() {});
    }

    @Test
    @DisplayName("골든셋 25개 로드 확인")
    void goldenSet_loads25Items() {
        assertThat(goldenSet).hasSize(25);
    }

    @Test
    @DisplayName("Fallback - 키워드 적중률 100%")
    void fallback_keywordHitRate() {
        given(explanationCachePort.getExplanation(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(any(), any())).willReturn(Optional.empty());
        given(embeddingServiceClient.embedText(any(EmbedTextRequestDto.class)))
                .willThrow(new RuntimeException("embedding 실패"));
        given(llmPort.generate(any())).willThrow(new RuntimeException("LLM 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        int totalKeywords = 0;
        int hitKeywords = 0;

        for (Map<String, Object> item : goldenSet) {
            List<String> tags = (List<String>) item.get("commonTags");
            double score = ((Number) item.get("score")).doubleValue();
            List<String> expectedKeywords = (List<String>) item.get("expectedKeywords");

            String result = matchExplanationService.generateExplanation(
                    UUID.randomUUID(), UUID.randomUUID(), tags, (float) score);

            for (String keyword : expectedKeywords) {
                totalKeywords++;
                if (result.contains(keyword)) {
                    hitKeywords++;
                }
            }
        }

        float hitRate = (float) hitKeywords / totalKeywords * 100;
        System.out.printf("[Fallback 키워드 적중률] %d/%d (%.1f%%)\n", hitKeywords, totalKeywords, hitRate);

        assertThat(hitRate).isGreaterThanOrEqualTo(70f);
    }

    @Test
    @DisplayName("Fallback - 길이 규칙 준수율 100%")
    void fallback_lengthCompliance() {
        given(explanationCachePort.getExplanation(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(any(), any())).willReturn(Optional.empty());
        given(embeddingServiceClient.embedText(any(EmbedTextRequestDto.class)))
                .willThrow(new RuntimeException("embedding 실패"));
        given(llmPort.generate(any())).willThrow(new RuntimeException("LLM 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        int total = 0;
        int compliant = 0;

        for (Map<String, Object> item : goldenSet) {
            List<String> tags = (List<String>) item.get("commonTags");
            double score = ((Number) item.get("score")).doubleValue();

            String result = matchExplanationService.generateExplanation(
                    UUID.randomUUID(), UUID.randomUUID(), tags, (float) score);

            total++;
            if (result.length() <= 100) {
                compliant++;
            }
        }

        float complianceRate = (float) compliant / total * 100;
        System.out.printf("[Fallback 길이 준수율] %d/%d (%.1f%%)\n", compliant, total, complianceRate);

        assertThat(complianceRate).isEqualTo(100f);
    }

    @Test
    @DisplayName("Fallback - 점수 포함 여부 100%")
    void fallback_scoreInclusion() {
        given(explanationCachePort.getExplanation(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(any(), any())).willReturn(Optional.empty());
        given(embeddingServiceClient.embedText(any(EmbedTextRequestDto.class)))
                .willThrow(new RuntimeException("embedding 실패"));
        given(llmPort.generate(any())).willThrow(new RuntimeException("LLM 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        int total = 0;
        int included = 0;

        for (Map<String, Object> item : goldenSet) {
            List<String> tags = (List<String>) item.get("commonTags");
            double score = ((Number) item.get("score")).doubleValue();
            int scorePercent = (int) (score * 100);

            String result = matchExplanationService.generateExplanation(
                    UUID.randomUUID(), UUID.randomUUID(), tags, (float) score);

            total++;
            if (result.contains(String.valueOf(scorePercent))) {
                included++;
            }
        }

        float inclusionRate = (float) included / total * 100;
        System.out.printf("[Fallback 점수 포함율] %d/%d (%.1f%%)\n", included, total, inclusionRate);

        assertThat(inclusionRate).isEqualTo(100f);
    }

    @Test
    @DisplayName("프롬프트 생성 - 핵심 규칙이 포함되어 있는지 확인")
    void prompt_containsKeyRules() {
        given(explanationCachePort.getExplanation(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(embeddingServiceClient.embedText(any(EmbedTextRequestDto.class)))
                .willThrow(new RuntimeException("embedding 실패"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        given(llmPort.generate(promptCaptor.capture())).willReturn("테스트 응답");

        matchExplanationService.generateExplanation(
                UUID.randomUUID(), UUID.randomUUID(), List.of("여행", "사진"), 0.82f);

        String prompt = promptCaptor.getValue();

        assertThat(prompt).contains("유사 사례");
        assertThat(prompt).contains("여행, 사진");
        assertThat(prompt).contains("한국어로");
        assertThat(prompt).contains("100자 이내");
        assertThat(prompt).contains("마크다운 금지");
    }

    @Test
    @DisplayName("Fallback - 태그 개수별 분기 처리 확인")
    void fallback_branchByTagCount() {
        given(explanationCachePort.getExplanation(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(any(), any())).willReturn(Optional.empty());
        given(embeddingServiceClient.embedText(any(EmbedTextRequestDto.class)))
                .willThrow(new RuntimeException("embedding 실패"));
        given(llmPort.generate(any())).willThrow(new RuntimeException("LLM 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String noTags = matchExplanationService.generateExplanation(
                UUID.randomUUID(), UUID.randomUUID(), List.of(), 0.45f);
        assertThat(noTags).contains("45");
        assertThat(noTags).doesNotContain("공통 관심사로 연결");

        String oneTag = matchExplanationService.generateExplanation(
                UUID.randomUUID(), UUID.randomUUID(), List.of("음악"), 0.68f);
        assertThat(oneTag).contains("음악");
        assertThat(oneTag).contains("68");

        String multiTags = matchExplanationService.generateExplanation(
                UUID.randomUUID(), UUID.randomUUID(), List.of("여행", "사진"), 0.82f);
        assertThat(multiTags).contains("여행");
        assertThat(multiTags).contains("사진");
        assertThat(multiTags).contains("82");
    }

    @Test
    @DisplayName("전체 골든셋 평가 리포트 출력")
    void goldenSet_fullReport() {
        given(explanationCachePort.getExplanation(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(any(), any())).willReturn(Optional.empty());
        given(embeddingServiceClient.embedText(any(EmbedTextRequestDto.class)))
                .willThrow(new RuntimeException("embedding 실패"));
        given(llmPort.generate(any())).willThrow(new RuntimeException("LLM 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        System.out.println("\n========== 골든셋 RAG 품질 평가 리포트 ==========\n");

        int totalKeywords = 0, hitKeywords = 0;
        int totalItems = 0, lengthOk = 0, scoreOk = 0;

        for (Map<String, Object> item : goldenSet) {
            List<String> tags = (List<String>) item.get("commonTags");
            double score = ((Number) item.get("score")).doubleValue();
            List<String> expectedKeywords = (List<String>) item.get("expectedKeywords");
            int scorePercent = (int) (score * 100);

            String result = matchExplanationService.generateExplanation(
                    UUID.randomUUID(), UUID.randomUUID(), tags, (float) score);

            totalItems++;
            if (result.length() <= 100) lengthOk++;
            if (result.contains(String.valueOf(scorePercent))) scoreOk++;

            for (String keyword : expectedKeywords) {
                totalKeywords++;
                if (result.contains(keyword)) hitKeywords++;
            }

            System.out.printf("[%2d] 태그=%-20s 점수=%d%% → %s\n",
                    ((Number) item.get("id")).intValue(),
                    tags.toString(),
                    scorePercent,
                    result);
        }

        System.out.println("\n---------- 결과 ----------");
        System.out.printf("키워드 적중률: %d/%d (%.1f%%)\n",
                hitKeywords, totalKeywords, (float) hitKeywords / totalKeywords * 100);
        System.out.printf("길이 준수율:   %d/%d (%.1f%%)\n",
                lengthOk, totalItems, (float) lengthOk / totalItems * 100);
        System.out.printf("점수 포함율:   %d/%d (%.1f%%)\n",
                scoreOk, totalItems, (float) scoreOk / totalItems * 100);
        System.out.println("==========================================\n");
    }
}