package com.sparta.ditto.match.infrastructure.rag;

import com.sparta.ditto.match.application.dto.EmbedTextRequestDto;
import com.sparta.ditto.match.application.dto.EmbedTextResponseDto;
import com.sparta.ditto.match.domain.entity.ExplanationExample;
import com.sparta.ditto.match.domain.repository.ExplanationExampleRepository;
import com.sparta.ditto.match.infrastructure.feign.EmbeddingServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 매칭 설명 RAG의 콜드 스타트 대비용 시드 데이터.
 * 서비스 초기엔 과거에 생성된 설명이 없으므로, 골든셋 예시를 미리 explanation_examples에 적재해둔다.
 * 시간이 지나 실제 LLM 생성 결과가 쌓이면(MatchExplanationService),
 * 이 시드 데이터의 비중은 자연히 줄어들고 실제 생성 이력이 검색에 더 많이 반영된다.
 *
 * 임베딩 계산은 embedding_service에 위임한다 (match_service는 임베딩 모델을 직접 갖지 않음).
 * 고정 ID(nameUUIDFromBytes)를 사용해 재기동 시 중복 적재 대신 upsert되도록 함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExplanationExampleSeedLoader implements ApplicationRunner {

    private static final String SOURCE_TYPE = "SEED";

    private final ExplanationExampleRepository explanationExampleRepository;
    private final EmbeddingServiceClient embeddingServiceClient;

    private record SeedExample(String key, String tags, int score, String answer) {
    }

    private static final List<SeedExample> SEEDS = List.of(
            new SeedExample(
                    "travel-photo",
                    "여행,사진",
                    82,
                    "여행과 사진을 함께 즐기는 감성이 딱 맞는 두 분이에요! 서로의 여행 사진 이야기로 대화가 끊이지 않을 것 같아요."
            ),
            new SeedExample(
                    "book-cafe",
                    "독서,카페",
                    75,
                    "독서와 카페를 좋아하는 취향이 잘 맞는 분들이에요. 조용한 카페에서 책 이야기 나눠보는 건 어떨까요?"
            ),
            new SeedExample(
                    "workout-gym",
                    "운동,헬스",
                    90,
                    "운동을 향한 열정이 두 분을 이어주었어요! 함께 운동 루틴을 공유하며 서로 동기부여가 될 것 같아요."
            ),
            new SeedExample(
                    "cooking-restaurant",
                    "요리,맛집",
                    78,
                    "요리와 맛집 탐방을 좋아하는 두 분의 만남이에요! 함께 새로운 레시피를 나누며 즐거운 시간을 보낼 것 같아요."
            )
    );

    @Override
    public void run(ApplicationArguments args) {
        int savedCount = 0;
        for (SeedExample seed : SEEDS) {
            try {
                saveSeed(seed);
                savedCount++;
            } catch (Exception e) {
                // 시드 적재 실패가 애플리케이션 기동 자체를 막으면 안 됨 (embedding_service 기동 지연 등)
                log.warn("[ExplanationRAG] 시드 적재 실패 key={} error={}", seed.key(), e.getMessage());
            }
        }
        log.info("[ExplanationRAG] 시드 예시 {}/{}건을 적재했습니다.", savedCount, SEEDS.size());
    }

    private void saveSeed(SeedExample seed) {
        String content = "Q: 공통태그=%s / 점수=%d%%\nA: %s"
                .formatted(seed.tags(), seed.score(), seed.answer());

        EmbedTextResponseDto embedded = embeddingServiceClient
                .embedText(new EmbedTextRequestDto(content))
                .getData();

        float[] vector = toFloatArray(embedded.vector());

        // 고정 키 기반 UUID → 재기동해도 같은 ID로 upsert (중복 적재 방지)
        UUID documentId = UUID.nameUUIDFromBytes(
                ("explanation-seed:" + seed.key()).getBytes(StandardCharsets.UTF_8)
        );

        explanationExampleRepository.save(ExplanationExample.of(
                documentId, content, vector, seed.tags(), seed.score(), SOURCE_TYPE));
    }

    private float[] toFloatArray(List<Float> vectorList) {
        float[] vector = new float[vectorList.size()];
        for (int i = 0; i < vectorList.size(); i++) {
            vector[i] = vectorList.get(i);
        }
        return vector;
    }
}
