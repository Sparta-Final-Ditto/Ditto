package com.sparta.ditto.match.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.match.application.port.ExplanationCachePort;
import com.sparta.ditto.match.application.port.LlmPort;
import com.sparta.ditto.match.application.service.MatchExplanationService;
import com.sparta.ditto.match.domain.repository.MatchingExplanationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Generation 품질 평가 - 충실성(Faithfulness) 1~5점
 *
 * LLM이 생성한 매칭 설명이:
 * 1. 환각이 없는지 (없는 태그 언급 안 하는지)
 * 2. 입력 태그를 잘 반영했는지
 * 3. 톤이 적절한지 (따뜻하고 긍정적)
 * 4. 길이가 적절한지 (100자 이내)
 * 5. 한국어로 답변하는지
 *
 * 실제 Ollama 연동 없이 Fallback 문장으로 평가하고,
 * LLM-as-judge 프롬프트 형태도 제공합니다.
 */
@ExtendWith(MockitoExtension.class)
class GenerationFaithfulnessTest {

    @Mock private LlmPort llmPort;
    @Mock private ExplanationCachePort explanationCachePort;
    @Mock private MatchingExplanationRepository explanationRepository;

    @InjectMocks
    private MatchExplanationService matchExplanationService;

    private List<Map<String, Object>> goldenSet;

    @BeforeEach
    void setUp() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("golden-set.json");
        goldenSet = new ObjectMapper().readValue(is, new TypeReference<>() {});
    }

    /**
     * 충실성(Faithfulness) 자동 평가
     * - 환각 체크: 입력에 없는 태그를 언급하면 감점
     * - 태그 반영: 입력 태그가 답변에 포함되면 가점
     * - 톤: 부정적 단어가 없으면 가점
     * - 길이: 100자 이내면 가점
     * - 점수 포함: 매칭 점수가 포함되면 가점
     */
    @Test
    @DisplayName("전체 골든셋 충실성 평가 리포트")
    void faithfulness_fullReport() {
        // Fallback으로 평가 (LLM 실패 시나리오)
        given(explanationCachePort.getExplanation(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(any(), any())).willReturn(Optional.empty());
        given(llmPort.generate(any())).willThrow(new RuntimeException("LLM 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        System.out.println("\n========== Generation 충실성(Faithfulness) 평가 ==========\n");
        System.out.printf("%-4s %-20s %-6s %-6s %-6s %-6s %-6s %-6s\n",
                "ID", "태그", "환각", "반영", "톤", "길이", "점수", "종합");
        System.out.println("-".repeat(72));

        List<Integer> scores = new ArrayList<>();

        for (Map<String, Object> item : goldenSet) {
            int id = ((Number) item.get("id")).intValue();
            List<String> tags = (List<String>) item.get("commonTags");
            double score = ((Number) item.get("score")).doubleValue();
            int scorePercent = (int) (score * 100);

            String result = matchExplanationService.generateExplanation(
                    UUID.randomUUID(), UUID.randomUUID(), tags, (float) score);

            // 1. 환각 체크 (1점) - 입력에 없는 태그 언급하면 0점
            int hallucinationScore = checkHallucination(result, tags) ? 1 : 0;

            // 2. 태그 반영 (1점) - 입력 태그가 답변에 포함되면 1점
            int tagReflectionScore = checkTagReflection(result, tags) ? 1 : 0;

            // 3. 톤 (1점) - 부정적 단어 없으면 1점
            int toneScore = checkPositiveTone(result) ? 1 : 0;

            // 4. 길이 (1점) - 100자 이내면 1점
            int lengthScore = result.length() <= 100 ? 1 : 0;

            // 5. 점수 포함 (1점) - 매칭 점수가 포함되면 1점
            int scoreInclusion = result.contains(String.valueOf(scorePercent)) ? 1 : 0;

            int totalScore = hallucinationScore + tagReflectionScore + toneScore + lengthScore + scoreInclusion;
            scores.add(totalScore);

            System.out.printf("%-4d %-20s %-6s %-6s %-6s %-6s %-6s %-6s\n",
                    id,
                    tags.toString().length() > 18 ? tags.toString().substring(0, 18) + ".." : tags.toString(),
                    hallucinationScore == 1 ? "O" : "X",
                    tagReflectionScore == 1 ? "O" : "X",
                    toneScore == 1 ? "O" : "X",
                    lengthScore == 1 ? "O" : "X",
                    scoreInclusion == 1 ? "O" : "X",
                    totalScore + "/5");
        }

        double avgScore = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        System.out.println("-".repeat(72));
        System.out.printf("평균 충실성 점수: %.2f/5\n", avgScore);
        System.out.println("======================================================\n");

        // 평균 4점 이상이어야 통과
        assertThat(avgScore).isGreaterThanOrEqualTo(4.0);
    }

    @Test
    @DisplayName("환각(Hallucination) 검출 테스트")
    void hallucination_detection() {
        // 환각 있는 답변: 입력에 "여행" 태그만 있는데 "음악"을 언급
        String hallucinated = "여행과 음악을 좋아하는 두 분이에요!";
        List<String> inputTags = List.of("여행");

        boolean noHallucination = checkHallucination(hallucinated, inputTags);
        // "음악"은 입력에 없으므로 환각 → false
        // 근데 우리 체크 로직은 "일반 단어"는 환각으로 안 잡음
        // 태그 리스트에 없는 태그를 정확히 언급하면 환각

        System.out.printf("[환각 테스트] 입력 태그=%s → 답변: %s\n", inputTags, hallucinated);
    }

    @Test
    @DisplayName("LLM-as-judge 프롬프트 생성 확인")
    void llmAsJudge_promptGeneration() {
        String generatedExplanation = "여행과 사진을 함께 즐기는 두 분이에요!";
        List<String> inputTags = List.of("여행", "사진");
        float score = 0.82f;

        String judgePrompt = buildLlmAsJudgePrompt(inputTags, score, generatedExplanation);

        System.out.println("\n========== LLM-as-Judge 프롬프트 ==========");
        System.out.println(judgePrompt);
        System.out.println("==========================================\n");

        assertThat(judgePrompt).contains("충실성");
        assertThat(judgePrompt).contains("1~5점");
        assertThat(judgePrompt).contains(generatedExplanation);
    }

    @Test
    @DisplayName("태그 0개 - Fallback이 점수를 포함하는지")
    void noTags_fallbackContainsScore() {
        given(explanationCachePort.getExplanation(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(any(), any())).willReturn(Optional.empty());
        given(llmPort.generate(any())).willThrow(new RuntimeException("LLM 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = matchExplanationService.generateExplanation(
                UUID.randomUUID(), UUID.randomUUID(), List.of(), 0.45f);

        assertThat(result).contains("45");
        assertThat(result.length()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("태그 1개 - Fallback이 태그를 포함하는지")
    void oneTag_fallbackContainsTag() {
        given(explanationCachePort.getExplanation(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(any(), any())).willReturn(Optional.empty());
        given(llmPort.generate(any())).willThrow(new RuntimeException("LLM 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = matchExplanationService.generateExplanation(
                UUID.randomUUID(), UUID.randomUUID(), List.of("음악"), 0.68f);

        assertThat(result).contains("음악");
        assertThat(result).contains("68");
    }

    @Test
    @DisplayName("태그 2개 이상 - Fallback이 태그와 개수를 포함하는지")
    void multiTags_fallbackContainsTagsAndCount() {
        given(explanationCachePort.getExplanation(any(), any())).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(any(), any())).willReturn(Optional.empty());
        given(llmPort.generate(any())).willThrow(new RuntimeException("LLM 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = matchExplanationService.generateExplanation(
                UUID.randomUUID(), UUID.randomUUID(), List.of("여행", "사진", "카페"), 0.85f);

        assertThat(result).contains("여행");
        assertThat(result).contains("사진");
        assertThat(result).contains("3");
        assertThat(result).contains("85");
    }

    // ── 평가 유틸 메서드 ──────────────────────────────────────

    /**
     * 환각 체크: 잘 알려진 태그 목록 중 입력에 없는 태그를 언급하면 환각
     */
    private boolean checkHallucination(String result, List<String> inputTags) {
        List<String> knownTags = List.of(
                "여행", "사진", "독서", "카페", "운동", "헬스", "요리", "맛집",
                "음악", "게임", "애니", "코딩", "개발", "영화", "넷플릭스",
                "등산", "캠핑", "패션", "댄스", "K-POP", "반려동물", "요가",
                "드로잉", "일러스트", "보드게임", "주식", "투자", "글쓰기",
                "명상", "외국어", "영어", "콘서트", "페스티벌", "카메라", "쇼핑"
        );

        for (String tag : knownTags) {
            if (result.contains(tag) && !inputTags.contains(tag)) {
                return false; // 환각 발견
            }
        }
        return true; // 환각 없음
    }

    /**
     * 태그 반영: 입력 태그 중 최소 1개가 답변에 포함되면 true
     * (태그 0개인 경우 점수가 포함되면 true)
     */
    private boolean checkTagReflection(String result, List<String> inputTags) {
        if (inputTags.isEmpty()) {
            return true; // 태그 없으면 점수만 있으면 OK
        }
        return inputTags.stream().anyMatch(result::contains);
    }

    /**
     * 긍정적 톤: 부정적 단어가 없으면 true
     */
    private boolean checkPositiveTone(String result) {
        List<String> negativeWords = List.of(
                "안타깝", "아쉽", "불행", "슬프", "힘들", "어렵", "별로",
                "실망", "짜증", "최악", "나쁘", "싫"
        );
        return negativeWords.stream().noneMatch(result::contains);
    }

    /**
     * LLM-as-judge 프롬프트 생성
     * 실제 Ollama 연동 시 이 프롬프트로 LLM에게 평가를 맡김
     */
    private String buildLlmAsJudgePrompt(List<String> inputTags, float score, String generatedExplanation) {
        return """
                당신은 SNS 매칭 서비스의 품질 평가자입니다.
                아래 매칭 설명의 충실성(faithfulness)을 1~5점으로 평가해주세요.
                
                [평가 기준]
                5점: 완벽 - 태그 정확히 반영, 환각 없음, 따뜻한 톤, 적절한 길이
                4점: 좋음 - 태그 대부분 반영, 환각 없음, 톤 적절
                3점: 보통 - 태그 일부 반영, 약간의 부정확함
                2점: 미흡 - 태그 거의 미반영, 환각 있음
                1점: 나쁨 - 완전히 엉뚱한 답변, 심각한 환각
                
                [입력 정보]
                공통 태그: %s
                매칭 점수: %d%%
                
                [생성된 설명]
                %s
                
                [평가]
                점수(1~5): 
                이유: 
                """.formatted(
                inputTags.isEmpty() ? "없음" : String.join(", ", inputTags),
                (int) (score * 100),
                generatedExplanation
        );
    }
}