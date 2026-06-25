package com.sparta.ditto.match.application.service;

import org.springframework.stereotype.Component;

@Component
public class CosineSimilarityCalculator {

    /**
     * 코사인 유사도 계산 후 [0, 1] 정규화
     * 원래 범위 [-1, 1] → (값 + 1) / 2 로 정규화
     */
    public float calculate(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null) return 0f;
        if (vectorA.length != vectorB.length) return 0f;

        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0f || normB == 0f) return 0f;

        float cosine = dotProduct / (float)(Math.sqrt(normA) * Math.sqrt(normB));

        // [-1, 1] → [0, 1] 정규화 (튜터님 피드백)
        return (cosine + 1f) / 2f;
    }
}