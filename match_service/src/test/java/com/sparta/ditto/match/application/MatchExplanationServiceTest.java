package com.sparta.ditto.match.application;

import com.sparta.ditto.match.application.port.ExplanationCachePort;
import com.sparta.ditto.match.application.port.LlmPort;
import com.sparta.ditto.match.application.service.MatchExplanationService;
import com.sparta.ditto.match.domain.entity.MatchingExplanation;
import com.sparta.ditto.match.domain.repository.MatchingExplanationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchExplanationServiceTest {

    @Mock private LlmPort llmPort;
    @Mock private ExplanationCachePort explanationCachePort;
    @Mock private MatchingExplanationRepository explanationRepository;

    @InjectMocks
    private MatchExplanationService matchExplanationService;

    @Test
    @DisplayName("Redis 캐시 히트 시 LLM을 호출하지 않는다")
    void generateExplanation_cacheHit_noLlmCall() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        given(explanationCachePort.getExplanation(userId, matchedUserId))
                .willReturn(Optional.of("캐시된 설명이에요!"));

        String result = matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of("여행", "카페"), 0.8f);

        assertThat(result).isEqualTo("캐시된 설명이에요!");
        verify(llmPort, never()).generate(any());
    }

    @Test
    @DisplayName("DB 히트 시 LLM을 호출하지 않고 Redis에 저장한다")
    void generateExplanation_dbHit_noLlmCallAndCachesSaved() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        MatchingExplanation saved = MatchingExplanation.of(userId, matchedUserId, "DB에서 가져온 설명");

        given(explanationCachePort.getExplanation(userId, matchedUserId)).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(userId, matchedUserId))
                .willReturn(Optional.of(saved));

        String result = matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of("여행"), 0.7f);

        assertThat(result).isEqualTo("DB에서 가져온 설명");
        verify(llmPort, never()).generate(any());
        verify(explanationCachePort).saveExplanation(userId, matchedUserId, "DB에서 가져온 설명");
    }

    @Test
    @DisplayName("캐시/DB 미스 시 LLM을 호출하고 저장한다")
    void generateExplanation_cacheMiss_callsLlmAndSaves() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(explanationCachePort.getExplanation(userId, matchedUserId)).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(userId, matchedUserId))
                .willReturn(Optional.empty());
        given(llmPort.generate(any())).willReturn("LLM이 생성한 설명이에요!");
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of("여행", "사진"), 0.85f);

        assertThat(result).isEqualTo("LLM이 생성한 설명이에요!");
        verify(llmPort).generate(any());
        verify(explanationCachePort).saveExplanation(userId, matchedUserId, "LLM이 생성한 설명이에요!");
        verify(explanationRepository).save(any());
    }

    @Test
    @DisplayName("LLM 호출 실패 시 Fallback 문장을 반환한다")
    void generateExplanation_llmFails_returnsFallback() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(explanationCachePort.getExplanation(userId, matchedUserId)).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(userId, matchedUserId))
                .willReturn(Optional.empty());
        given(llmPort.generate(any())).willThrow(new RuntimeException("Ollama 연결 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of("여행", "카페"), 0.8f);

        assertThat(result).isNotBlank();
        assertThat(result).contains("80");  // fallback에 점수 포함
    }

    @Test
    @DisplayName("공통 태그 없을 때 Fallback 문장에 점수가 포함된다")
    void generateExplanation_noTags_fallbackContainsScore() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(explanationCachePort.getExplanation(userId, matchedUserId)).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(userId, matchedUserId))
                .willReturn(Optional.empty());
        given(llmPort.generate(any())).willThrow(new RuntimeException("실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of(), 0.75f);

        assertThat(result).contains("75");
    }
}