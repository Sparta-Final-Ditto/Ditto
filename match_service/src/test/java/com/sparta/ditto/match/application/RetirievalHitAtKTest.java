package com.sparta.ditto.match.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.match.application.service.CosineSimilarityCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retrieval 품질 평가 - Hit@K 자동 측정
 *
 * "이 유저에게 이 후보들이 나와야 한다" 골든셋으로
 * 코사인 유사도 + 태그 유사도 기반 검색 품질을 측정한다.
 *
 * Hit@K = 상위 K개 결과 중 정답이 1개 이상 포함된 비율
 * Recall@K = 상위 K개 결과 중 정답이 포함된 개수 / 전체 정답 수
 */
class RetrievalHitAtKTest {

    private CosineSimilarityCalculator calculator;
    private List<Map<String, Object>> goldenSet;

    @BeforeEach
    void setUp() throws Exception {
        calculator = new CosineSimilarityCalculator();
        InputStream is = getClass().getClassLoader().getResourceAsStream("golden-set-retrieval.json");
        goldenSet = new ObjectMapper().readValue(is, new TypeReference<>() {});
    }

    @Test
    @DisplayName("골든셋 로드 확인")
    void goldenSet_loadsCorrectly() {
        assertThat(goldenSet).isNotEmpty();
        System.out.printf("[Retrieval 골든셋] %d개 로드 완료\n", goldenSet.size());
    }

    @Test
    @DisplayName("Hit@K 평가 - 상위 K개에 정답이 포함되는 비율")
    void hitAtK_evaluation() {
        System.out.println("\n========== Retrieval Hit@K 평가 ==========\n");

        int totalQueries = 0;
        int hitCount = 0;

        for (Map<String, Object> item : goldenSet) {
            int id = ((Number) item.get("id")).intValue();
            String description = (String) item.get("description");
            int K = ((Number) item.get("K")).intValue();

            Map<String, Object> queryUser = (Map<String, Object>) item.get("queryUser");
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) item.get("candidates");
            List<String> expectedTopK = (List<String>) item.get("expectedTopK");

            // 쿼리 유저 벡터, 태그
            float[] queryVector = toFloatArray((List<Number>) queryUser.get("vector"));
            Set<String> queryTags = new HashSet<>((List<String>) queryUser.get("tags"));

            // 후보별 점수 계산 (코사인 0.5 + 태그 0.5)
            List<Map.Entry<String, Float>> scored = new ArrayList<>();
            for (Map<String, Object> candidate : candidates) {
                String candidateId = (String) candidate.get("userId");
                float[] candidateVector = toFloatArray((List<Number>) candidate.get("vector"));
                Set<String> candidateTags = new HashSet<>((List<String>) candidate.get("tags"));

                float cosineScore = calculator.calculate(queryVector, candidateVector);
                float tagScore = calculateTagSimilarity(queryTags, candidateTags);
                float finalScore = cosineScore * 0.5f + tagScore * 0.5f;

                scored.add(Map.entry(candidateId, finalScore));
            }

            // 점수 높은 순 정렬 → 상위 K개
            List<String> topK = scored.stream()
                    .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                    .limit(K)
                    .map(Map.Entry::getKey)
                    .toList();

            // Hit 체크: 상위 K개에 기대 후보가 1개 이상 포함되는지
            boolean hit = topK.stream().anyMatch(expectedTopK::contains);

            // Recall: 상위 K개에 기대 후보가 몇 개 포함되는지
            long recallCount = topK.stream().filter(expectedTopK::contains).count();
            float recall = (float) recallCount / expectedTopK.size();

            totalQueries++;
            if (hit) hitCount++;

            System.out.printf("[%d] %s\n", id, description);
            System.out.printf("    기대: %s\n", expectedTopK);
            System.out.printf("    실제: %s\n", topK);
            System.out.printf("    Hit: %s | Recall@%d: %.1f%% (%d/%d)\n\n",
                    hit ? "O" : "X", K, recall * 100, recallCount, expectedTopK.size());
        }

        float hitRate = (float) hitCount / totalQueries * 100;
        System.out.println("---------- 결과 ----------");
        System.out.printf("Hit@K: %d/%d (%.1f%%)\n", hitCount, totalQueries, hitRate);
        System.out.println("==========================\n");

        // Hit@K 80% 이상이어야 통과
        assertThat(hitRate).isGreaterThanOrEqualTo(80f);
    }

    @Test
    @DisplayName("Recall@K 평가 - 상위 K개에 정답이 몇 개 포함되는지")
    void recallAtK_evaluation() {
        System.out.println("\n========== Retrieval Recall@K 평가 ==========\n");

        float totalRecall = 0f;
        int totalQueries = 0;

        for (Map<String, Object> item : goldenSet) {
            int K = ((Number) item.get("K")).intValue();

            Map<String, Object> queryUser = (Map<String, Object>) item.get("queryUser");
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) item.get("candidates");
            List<String> expectedTopK = (List<String>) item.get("expectedTopK");

            float[] queryVector = toFloatArray((List<Number>) queryUser.get("vector"));
            Set<String> queryTags = new HashSet<>((List<String>) queryUser.get("tags"));

            List<Map.Entry<String, Float>> scored = new ArrayList<>();
            for (Map<String, Object> candidate : candidates) {
                String candidateId = (String) candidate.get("userId");
                float[] candidateVector = toFloatArray((List<Number>) candidate.get("vector"));
                Set<String> candidateTags = new HashSet<>((List<String>) candidate.get("tags"));

                float cosineScore = calculator.calculate(queryVector, candidateVector);
                float tagScore = calculateTagSimilarity(queryTags, candidateTags);
                float finalScore = cosineScore * 0.5f + tagScore * 0.5f;

                scored.add(Map.entry(candidateId, finalScore));
            }

            List<String> topK = scored.stream()
                    .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                    .limit(K)
                    .map(Map.Entry::getKey)
                    .toList();

            long recallCount = topK.stream().filter(expectedTopK::contains).count();
            float recall = (float) recallCount / expectedTopK.size();

            totalRecall += recall;
            totalQueries++;
        }

        float avgRecall = totalRecall / totalQueries * 100;
        System.out.printf("평균 Recall@K: %.1f%%\n", avgRecall);
        System.out.println("================================\n");

        // 평균 Recall 70% 이상이어야 통과
        assertThat(avgRecall).isGreaterThanOrEqualTo(70f);
    }

    @Test
    @DisplayName("코사인 vs 태그 기여도 분석")
    void contribution_analysis() {
        System.out.println("\n========== 코사인 vs 태그 기여도 분석 ==========\n");

        for (Map<String, Object> item : goldenSet) {
            int id = ((Number) item.get("id")).intValue();

            Map<String, Object> queryUser = (Map<String, Object>) item.get("queryUser");
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) item.get("candidates");

            float[] queryVector = toFloatArray((List<Number>) queryUser.get("vector"));
            Set<String> queryTags = new HashSet<>((List<String>) queryUser.get("tags"));

            System.out.printf("[%d] 쿼리 태그: %s\n", id, queryTags);

            for (Map<String, Object> candidate : candidates) {
                String candidateId = (String) candidate.get("userId");
                float[] candidateVector = toFloatArray((List<Number>) candidate.get("vector"));
                Set<String> candidateTags = new HashSet<>((List<String>) candidate.get("tags"));

                float cosine = calculator.calculate(queryVector, candidateVector);
                float tag = calculateTagSimilarity(queryTags, candidateTags);
                float total = cosine * 0.5f + tag * 0.5f;

                System.out.printf("    %-25s 코사인=%.3f 태그=%.3f 최종=%.3f\n",
                        candidateId, cosine, tag, total);
            }
            System.out.println();
        }
    }

    // 유틸 메서드
    private float[] toFloatArray(List<Number> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i).floatValue();
        }
        return arr;
    }

    private float calculateTagSimilarity(Set<String> tagsA, Set<String> tagsB) {
        if (tagsA == null || tagsB == null) return 0f;
        if (tagsA.isEmpty() && tagsB.isEmpty()) return 0f;
        Set<String> intersection = new HashSet<>(tagsA);
        intersection.retainAll(tagsB);
        Set<String> union = new HashSet<>(tagsA);
        union.addAll(tagsB);
        return union.isEmpty() ? 0f : (float) intersection.size() / union.size();
    }
}