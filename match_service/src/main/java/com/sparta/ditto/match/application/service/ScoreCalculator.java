package com.sparta.ditto.match.application.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * [Application Layer] 매칭 점수 계산기
 *
 * 관계:
 * - MatchService에서 주입받아 사용
 * - 외부 의존성 없음 (순수 Java 로직)
 * - 세 가지 점수를 계산해서 final_score 생성
 *
 * 점수 구성:
 * - 코사인 유사도 (60%) → pgvector에서 계산한 벡터 유사도
 * - 자카드 유사도 (20%) → Kafka POST_CREATED 이벤트의 태그 겹침
 * - 시간대 일치 점수 (20%) → 활동 시간대 일치 여부
 */
@Component
public class ScoreCalculator {

    /**
     * 최종 점수 계산
     * Final Score = 코사인유사도×0.6 + 자카드유사도×0.2 + 시간대점수×0.2
     */
    public float calculateFinalScore(
            float similarityScore, // pgvector 코사인 유사도
            float tagScore,        // 자카드 유사도
            float timeScore        // 시간대 일치 점수
    ) {
        return (similarityScore * 0.6f) + (tagScore * 0.2f) + (timeScore * 0.2f);
    }

    /**
     * 자카드 유사도 계산
     * = 공통 태그 수 / 전체 태그 수
     * Kafka POST_CREATED 이벤트에서 받아온 태그 목록으로 계산
     *
     * 예시)
     * A 태그: [#혼공, #카페, #취준]
     * B 태그: [#혼공, #운동]
     * 공통: [#혼공] → 1개
     * 전체: [#혼공, #카페, #취준, #운동] → 4개
     * 자카드 유사도 = 1/4 = 0.25
     */
    public float calculateJaccardSimilarity(
            List<String> tagsA,
            List<String> tagsB
    ) {
        if (tagsA.isEmpty() && tagsB.isEmpty()) {
            return 0f;
        }

        Set<String> intersection = new HashSet<>(tagsA);
        intersection.retainAll(new HashSet<>(tagsB)); // 교집합

        Set<String> union = new HashSet<>(tagsA);
        union.addAll(tagsB); // 합집합

        return (float) intersection.size() / union.size();
    }

    /**
     * 시간대 범주화
     * 새벽(0~6시) / 오전(6~12시) / 오후(12~18시) / 저녁(18~24시)
     * 게시글 createdAt 기준으로 범주화
     */
    public String categorizeTimeSlot(Instant instant) {
        int hour = instant.atZone(ZoneId.of("Asia/Seoul")).getHour();
        if (hour >= 0 && hour < 6) {
            return "새벽";
        }
        if (hour >= 6 && hour < 12) {
            return "오전";
        }
        if (hour >= 12 && hour < 18) {
            return "오후";
        }
        return "저녁";
    }

    /**
     * 시간대 일치 점수
     * 같은 시간대면 1.0, 다르면 0.0
     */
    public float calculateTimeScore(String slotA, String slotB) {
        return slotA.equals(slotB) ? 1.0f : 0.0f;
    }
}
