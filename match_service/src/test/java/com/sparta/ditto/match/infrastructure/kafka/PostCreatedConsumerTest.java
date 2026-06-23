package com.sparta.ditto.match.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.match.application.service.ScoreCalculator;
import com.sparta.ditto.match.infrastructure.redis.MatchCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostCreatedConsumerTest {

    @Mock
    private MatchCacheService matchCacheService;
    @Mock
    private ScoreCalculator scoreCalculator;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PostCreatedConsumer postCreatedConsumer;

    @Test
    @DisplayName("유효한 메시지 소비 시 태그와 시간대를 캐싱한다")
    void consume_validMessage_cachesTagsAndTimeSlot() throws Exception {
        UUID userId = UUID.randomUUID();
        List<String> tags = List.of("#혼공", "#카페");
        Instant createdAt = Instant.now();

        PostCreatedEvent.Payload payload = new PostCreatedEvent.Payload(
                UUID.randomUUID(), userId, "content", tags, "강남구", 37.5, 127.0, createdAt);
        PostCreatedEvent event = new PostCreatedEvent("id1", "POST_CREATED", "now", payload);
        String message = "{}";

        given(objectMapper.readValue(message, PostCreatedEvent.class)).willReturn(event);
        given(scoreCalculator.categorizeTimeSlot(createdAt)).willReturn("오전");

        postCreatedConsumer.consume(message);

        verify(matchCacheService).cacheUserTags(userId, tags);
        verify(matchCacheService).cacheUserTimeSlot(userId, "오전");
    }

    @Test
    @DisplayName("파싱 실패 시 예외를 삼키고 정상 종료된다")
    void consume_invalidMessage_doesNotThrow() throws Exception {
        String invalidMessage = "invalid-json";

        given(objectMapper.readValue(invalidMessage, PostCreatedEvent.class))
                .willThrow(new RuntimeException("parse error"));

        assertThatCode(() -> postCreatedConsumer.consume(invalidMessage))
                .doesNotThrowAnyException();
    }
}
