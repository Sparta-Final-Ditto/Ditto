package com.sparta.ditto.match.application;

import com.sparta.ditto.match.application.service.ScoreCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;

class ScoreCalculatorTest {

    private final ScoreCalculator scoreCalculator = new ScoreCalculator();

    @Test
    @DisplayName("자카드 유사도 - 태그 완전히 같으면 1.0")
    void calculateJaccardSimilarity_sameTags_returnsOne() {
        List<String> tagsA = List.of("#혼공", "#카페", "#취준");
        List<String> tagsB = List.of("#혼공", "#카페", "#취준");

        float result = scoreCalculator.calculateJaccardSimilarity(tagsA, tagsB);

        assertThat(result).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("자카드 유사도 - 태그 전혀 다르면 0.0")
    void calculateJaccardSimilarity_differentTags_returnsZero() {
        List<String> tagsA = List.of("#혼공", "#카페");
        List<String> tagsB = List.of("#운동", "#헬스");

        float result = scoreCalculator.calculateJaccardSimilarity(tagsA, tagsB);

        assertThat(result).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("자카드 유사도 - 태그 일부 겹치면 비율 반환")
    void calculateJaccardSimilarity_partialTags_returnsRatio() {
        List<String> tagsA = List.of("#혼공", "#카페", "#취준");
        List<String> tagsB = List.of("#혼공", "#운동");

        float result = scoreCalculator.calculateJaccardSimilarity(tagsA, tagsB);

        // 공통: [#혼공] = 1개
        // 전체: [#혼공, #카페, #취준, #운동] = 4개
        // 1/4 = 0.25
        assertThat(result).isEqualTo(0.25f);
    }

    @Test
    @DisplayName("자카드 유사도 - 둘 다 비어있으면 0.0")
    void calculateJaccardSimilarity_emptyTags_returnsZero() {
        List<String> tagsA = List.of();
        List<String> tagsB = List.of();

        float result = scoreCalculator.calculateJaccardSimilarity(tagsA, tagsB);

        assertThat(result).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("시간대 범주화 - 새벽 (0~6시)")
    void categorizeTimeSlot_dawn() {
        Instant dawn = LocalDateTime.of(2026, 6, 23, 3, 0)
                .atZone(ZoneId.of("Asia/Seoul")).toInstant();

        String result = scoreCalculator.categorizeTimeSlot(dawn);

        assertThat(result).isEqualTo("새벽");
    }

    @Test
    @DisplayName("시간대 범주화 - 오전 (6~12시)")
    void categorizeTimeSlot_morning() {
        Instant morning = LocalDateTime.of(2026, 6, 23, 9, 0)
                .atZone(ZoneId.of("Asia/Seoul")).toInstant();

        String result = scoreCalculator.categorizeTimeSlot(morning);

        assertThat(result).isEqualTo("오전");
    }

    @Test
    @DisplayName("시간대 범주화 - 오후 (12~18시)")
    void categorizeTimeSlot_afternoon() {
        Instant afternoon = LocalDateTime.of(2026, 6, 23, 14, 0)
                .atZone(ZoneId.of("Asia/Seoul")).toInstant();

        String result = scoreCalculator.categorizeTimeSlot(afternoon);

        assertThat(result).isEqualTo("오후");
    }

    @Test
    @DisplayName("시간대 범주화 - 저녁 (18~24시)")
    void categorizeTimeSlot_evening() {
        Instant evening = LocalDateTime.of(2026, 6, 23, 20, 0)
                .atZone(ZoneId.of("Asia/Seoul")).toInstant();

        String result = scoreCalculator.categorizeTimeSlot(evening);

        assertThat(result).isEqualTo("저녁");
    }

    @Test
    @DisplayName("시간대 일치 - 같은 시간대면 1.0")
    void calculateTimeScore_sameSlot_returnsOne() {
        float result = scoreCalculator.calculateTimeScore("저녁", "저녁");

        assertThat(result).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("시간대 불일치 - 다른 시간대면 0.0")
    void calculateTimeScore_differentSlot_returnsZero() {
        float result = scoreCalculator.calculateTimeScore("새벽", "저녁");

        assertThat(result).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("최종 점수 계산 - 가중치 적용")
    void calculateFinalScore_returnsWeightedScore() {
        float similarity = 0.8f;
        float tagScore = 0.5f;
        float timeScore = 1.0f;

        float result = scoreCalculator.calculateFinalScore(
                similarity, tagScore, timeScore
        );

        // isEqualTo 대신 isCloseTo 사용
        assertThat(result).isCloseTo(0.78f, within(0.001f));
    }
}