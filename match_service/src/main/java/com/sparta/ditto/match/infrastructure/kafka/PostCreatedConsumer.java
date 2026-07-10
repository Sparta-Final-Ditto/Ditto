package com.sparta.ditto.match.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.match.application.service.ScoreCalculator;
import com.sparta.ditto.match.infrastructure.redis.MatchCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// infrastructure/kafka/PostCreatedConsumer.java
@Component
@RequiredArgsConstructor
@Slf4j
public class PostCreatedConsumer {

    private final MatchCacheService matchCacheService;
    private final ScoreCalculator scoreCalculator;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "post-events",
            groupId = "match-service"
    )
    public void consume(String message) {
        try {
            PostCreatedEvent event = objectMapper
                    .readValue(message, PostCreatedEvent.class);

            // 1. 태그 Redis에 저장
            matchCacheService.cacheUserTags(
                    event.getUserId(),
                    event.getTags()
            );

            // 2. 시간대 범주화 후 저장
            String timeSlot = scoreCalculator
                    .categorizeTimeSlot(event.getCreatedAt());
            matchCacheService.cacheUserTimeSlot(
                    event.getUserId(),
                    timeSlot
            );

            log.info("POST_CREATED 수신: userId={}", event.getUserId());

        } catch (Exception e) {
            log.error("Kafka 이벤트 처리 실패: {}", e.getMessage());
        }
    }
}
