package com.sparta.ditto.match.infrastructure.rag;

import com.sparta.ditto.match.application.dto.EmbedTextRequestDto;
import com.sparta.ditto.match.application.dto.EmbedTextResponseDto;
import com.sparta.ditto.match.domain.entity.ExplanationExample;
import com.sparta.ditto.match.domain.repository.ExplanationExampleRepository;
import com.sparta.ditto.match.infrastructure.feign.EmbeddingServiceClient;
import com.sparta.ditto.match.infrastructure.feign.FeignEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExplanationExampleSeedLoaderTest {

    @Mock private ExplanationExampleRepository explanationExampleRepository;
    @Mock private EmbeddingServiceClient embeddingServiceClient;

    @InjectMocks
    private ExplanationExampleSeedLoader seedLoader;

    private static final List<Float> FAKE_VECTOR = List.of(0.1f, 0.2f, 0.3f);

    @Test
    @DisplayName("시드 데이터가 정상적으로 로드되고 저장된다")
    void run_loadsAndSavesSeeds() {
        given(embeddingServiceClient.embedText(any(EmbedTextRequestDto.class)))
                .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", new EmbedTextResponseDto(FAKE_VECTOR, 3), null));
        given(explanationExampleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        seedLoader.run(null);

        verify(embeddingServiceClient, atLeastOnce()).embedText(any());
        verify(explanationExampleRepository, atLeastOnce()).save(any(ExplanationExample.class));
    }

    @Test
    @DisplayName("embedding_service 실패 시에도 예외 없이 종료된다")
    void run_embeddingServiceFails_noException() {
        given(embeddingServiceClient.embedText(any(EmbedTextRequestDto.class)))
                .willThrow(new RuntimeException("embedding 실패"));

        seedLoader.run(null);

        verify(explanationExampleRepository, never()).save(any());
    }
}