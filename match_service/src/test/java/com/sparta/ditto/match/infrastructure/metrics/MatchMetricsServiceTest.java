package com.sparta.ditto.match.infrastructure.metrics;

import com.sparta.ditto.match.domain.entity.MatchStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchMetricsServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private MatchMetricsService matchMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        matchMetricsService = new MatchMetricsService(meterRegistry);
    }

    @Test
    @DisplayName("매칭 결과 상태별로 카운터가 증가한다")
    void recordMatchResult_incrementsCounter() {
        matchMetricsService.recordMatchResult(MatchStatus.ACCEPTED);

        double count = meterRegistry.counter("match.result", "status", "ACCEPTED").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("유사도 점수가 summary에 기록된다")
    void recordSimilarityScore_recordsToSummary() {
        matchMetricsService.recordSimilarityScore(0.85f);

        assertThat(meterRegistry.summary("match.similarity.score").count()).isEqualTo(1);
        assertThat(meterRegistry.summary("match.similarity.score").totalAmount())
                .isCloseTo(0.85, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("최종 점수가 summary에 기록된다")
    void recordFinalScore_recordsToSummary() {
        matchMetricsService.recordFinalScore(0.7f);

        assertThat(meterRegistry.summary("match.final.score").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("LLM 성공 시 llm_success 태그로 카운트된다")
    void recordExplanationResult_llmSuccess_countsWithSuccessTag() {
        matchMetricsService.recordExplanationResult(true);

        double count = meterRegistry.counter("match.explanation", "result", "llm_success").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("LLM 실패 시 fallback 태그로 카운트된다")
    void recordExplanationResult_llmFails_countsWithFallbackTag() {
        matchMetricsService.recordExplanationResult(false);

        double count = meterRegistry.counter("match.explanation", "result", "fallback").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("캐시 히트/미스가 태그별로 카운트된다")
    void recordCacheHit_recordsHitAndMiss() {
        matchMetricsService.recordCacheHit("userTags", true);
        matchMetricsService.recordCacheHit("userTags", false);

        assertThat(meterRegistry.counter("match.cache", "type", "userTags", "result", "hit").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("match.cache", "type", "userTags", "result", "miss").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("매칭 생성 응답 시간이 타이머에 기록된다")
    void recordMatchLatency_recordsToTimer() {
        matchMetricsService.recordMatchLatency(250L);

        assertThat(meterRegistry.timer("match.create.latency").count()).isEqualTo(1);
    }
}