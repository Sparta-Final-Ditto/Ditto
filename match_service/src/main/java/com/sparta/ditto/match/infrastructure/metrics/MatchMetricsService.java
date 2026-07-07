package com.sparta.ditto.match.infrastructure.metrics;

import com.sparta.ditto.match.domain.entity.MatchStatus;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class MatchMetricsService {

    private final MeterRegistry meterRegistry;

    // 매칭 결과 카운트 (PENDING/ACCEPTED/REJECTED)
    public void recordMatchResult(MatchStatus status) {
        meterRegistry.counter("match.result", "status", status.name()).increment();
    }

    // 유사도 점수 분포
    public void recordSimilarityScore(float score) {
        meterRegistry.summary("match.similarity.score").record(score);
    }

    // 최종 점수 분포
    public void recordFinalScore(float score) {
        meterRegistry.summary("match.final.score").record(score);
    }

    // RAG 설명 생성 결과 (LLM 성공 / Fallback)
    public void recordExplanationResult(boolean llmSuccess) {
        meterRegistry.counter("match.explanation",
                "result", llmSuccess ? "llm_success" : "fallback").increment();
    }

    // Redis 캐시 히트율
    public void recordCacheHit(String cacheType, boolean hit) {
        meterRegistry.counter("match.cache",
                "type", cacheType,
                "result", hit ? "hit" : "miss").increment();
    }

    // 매칭 생성 응답 시간
    public void recordMatchLatency(long millis) {
        meterRegistry.timer("match.create.latency")
                .record(millis, TimeUnit.MILLISECONDS);
    }
}