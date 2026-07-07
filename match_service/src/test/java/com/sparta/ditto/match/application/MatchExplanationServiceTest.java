package com.sparta.ditto.match.application;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.match.application.dto.EmbedTextRequestDto;
import com.sparta.ditto.match.application.dto.EmbedTextResponseDto;
import com.sparta.ditto.match.application.port.ExplanationCachePort;
import com.sparta.ditto.match.application.port.LlmPort;
import com.sparta.ditto.match.application.service.MatchExplanationService;
import com.sparta.ditto.match.domain.entity.MatchingExplanation;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchExplanationServiceTest {

    @Mock private LlmPort llmPort;
    @Mock private ExplanationCachePort explanationCachePort;
    @Mock private MatchingExplanationRepository explanationRepository;
    @Mock private ExplanationExampleRepository explanationExampleRepository;
    @Mock private EmbeddingServiceClient embeddingServiceClient;

    private MatchExplanationService matchExplanationService;

    private static final List<Float> FAKE_VECTOR = List.of(0.1f, 0.2f, 0.3f);

    @BeforeEach
    void setUp() {
        matchExplanationService = new MatchExplanationService(
                llmPort, explanationCachePort, explanationRepository,
                explanationExampleRepository, embeddingServiceClient);
    }

    private void stubEmbedText() {
        given(embeddingServiceClient.embedText(any(EmbedTextRequestDto.class)))
                .willReturn(ApiResponse.success(new EmbedTextResponseDto(FAKE_VECTOR, 3)));
    }

    @Test
    @DisplayName("Redis 캐시 히트 시 LLM도, 임베딩 호출도 하지 않는다")
    void generateExplanation_cacheHit_noLlmCall() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        given(explanationCachePort.getExplanation(userId, matchedUserId))
                .willReturn(Optional.of("캐시된 설명이에요!"));

        String result = matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of("여행", "카페"), 0.8f);

        assertThat(result).isEqualTo("캐시된 설명이에요!");
        verify(llmPort, never()).generate(any());
        verify(embeddingServiceClient, never()).embedText(any());
    }

    @Test
    @DisplayName("DB 히트 시 LLM도, 임베딩 호출도 하지 않고 Redis에 저장한다")
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
        verify(embeddingServiceClient, never()).embedText(any());
        verify(explanationCachePort).saveExplanation(userId, matchedUserId, "DB에서 가져온 설명");
    }

    @Test
    @DisplayName("캐시/DB 미스 시 임베딩 검색 → LLM 호출 → 저장까지 이어진다")
    void generateExplanation_cacheMiss_callsLlmAndSaves() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(explanationCachePort.getExplanation(userId, matchedUserId)).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(userId, matchedUserId))
                .willReturn(Optional.empty());
        stubEmbedText();
        given(explanationExampleRepository.findSimilarExamples(anyString(), anyDouble(), anyInt()))
                .willReturn(List.of());
        given(llmPort.generate(any())).willReturn("LLM이 생성한 설명이에요!");
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of("여행", "사진"), 0.85f);

        assertThat(result).isEqualTo("LLM이 생성한 설명이에요!");
        verify(embeddingServiceClient, atLeastOnce()).embedText(any());
        verify(llmPort).generate(any());
        verify(explanationCachePort).saveExplanation(userId, matchedUserId, "LLM이 생성한 설명이에요!");
        verify(explanationRepository).save(any());
    }

    @Test
    @DisplayName("벡터 검색으로 찾은 과거 사례가 LLM 프롬프트에 포함된다")
    void generateExplanation_retrievedExamples_injectedIntoPrompt() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(explanationCachePort.getExplanation(userId, matchedUserId)).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(userId, matchedUserId))
                .willReturn(Optional.empty());
        stubEmbedText();

        Object[] row = new Object[]{
                UUID.randomUUID(),
                "Q: 공통태그=등산,캠핑 / 점수=88%\nA: 자연을 사랑하는 두 분, 함께 산에 오르며 특별한 추억을 쌓아보세요!",
                0.91
        };
        given(explanationExampleRepository.findSimilarExamples(anyString(), anyDouble(), anyInt()))
                .willReturn(List.<Object[]>of(row));
        given(llmPort.generate(any())).willReturn("생성된 설명");
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        matchExplanationService.generateExplanation(userId, matchedUserId, List.of("등산"), 0.8f);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmPort).generate(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("등산,캠핑");
        assertThat(promptCaptor.getValue()).contains("함께 산에 오르며");
    }

    @Test
    @DisplayName("LLM 생성 성공 시 embedding_service로 재임베딩 후 저장한다")
    void generateExplanation_llmSuccess_indexesExample() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(explanationCachePort.getExplanation(userId, matchedUserId)).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(userId, matchedUserId))
                .willReturn(Optional.empty());
        stubEmbedText();
        given(explanationExampleRepository.findSimilarExamples(anyString(), anyDouble(), anyInt()))
                .willReturn(List.of());
        given(llmPort.generate(any())).willReturn("LLM이 생성한 설명이에요!");
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of("여행", "사진"), 0.85f);

        // 검색 1회 + 적재 전 재임베딩 1회 = 최소 2회 embedText 호출
        verify(embeddingServiceClient, times(2)).embedText(any());
        verify(explanationExampleRepository).save(any());
    }

    @Test
    @DisplayName("LLM 호출 실패 시 Fallback 문장을 반환하고, 새 예시는 저장하지 않는다")
    void generateExplanation_llmFails_returnsFallback() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(explanationCachePort.getExplanation(userId, matchedUserId)).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(userId, matchedUserId))
                .willReturn(Optional.empty());
        stubEmbedText();
        given(explanationExampleRepository.findSimilarExamples(anyString(), anyDouble(), anyInt()))
                .willReturn(List.of());
        given(llmPort.generate(any())).willThrow(new RuntimeException("Ollama 연결 실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of("여행", "카페"), 0.8f);

        assertThat(result).isNotBlank();
        assertThat(result).contains("80");  // fallback에 점수 포함
        verify(explanationExampleRepository, never()).save(any());
    }

    @Test
    @DisplayName("공통 태그 없을 때 Fallback 문장에 점수가 포함된다")
    void generateExplanation_noTags_fallbackContainsScore() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(explanationCachePort.getExplanation(userId, matchedUserId)).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(userId, matchedUserId))
                .willReturn(Optional.empty());
        stubEmbedText();
        given(explanationExampleRepository.findSimilarExamples(anyString(), anyDouble(), anyInt()))
                .willReturn(List.of());
        given(llmPort.generate(any())).willThrow(new RuntimeException("실패"));
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of(), 0.75f);

        assertThat(result).contains("75");
    }

    @Test
    @DisplayName("embedding_service 호출이 실패해도 설명 생성 자체는 실패하지 않는다")
    void generateExplanation_embeddingServiceFails_stillGeneratesExplanation() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(explanationCachePort.getExplanation(userId, matchedUserId)).willReturn(Optional.empty());
        given(explanationRepository.findByUserIdAndMatchedUserId(userId, matchedUserId))
                .willReturn(Optional.empty());
        given(embeddingServiceClient.embedText(any(EmbedTextRequestDto.class)))
                .willThrow(new RuntimeException("embedding_service 연결 실패"));
        given(llmPort.generate(any())).willReturn("생성된 설명");
        given(explanationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        String result = matchExplanationService.generateExplanation(
                userId, matchedUserId, List.of("여행"), 0.8f);

        assertThat(result).isEqualTo("생성된 설명");
    }
}
