package com.sparta.ditto.notification.infrastructure.kafka;

import com.sparta.ditto.notification.application.NotificationEventHandler;
import com.sparta.ditto.notification.application.dto.PostNotificationCommand;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostEventConsumer - post-events Envelope 2단 파싱 → command 변환 → handler 위임")
class PostEventConsumerTest {

    @Mock
    private NotificationEventHandler handler;

    @InjectMocks
    private PostEventConsumer consumer;

    // ── 공통 고정 값 ──────────────────────────────────────────────────────────
    private static final String ACTOR_UUID   = "7b9f6e22-03e7-4b59-a9a4-95de4e2f1234";
    private static final String OWNER_UUID   = "1caa0424-5b1e-4f8a-90b5-bc1d6e2a5678";
    private static final String POST_ID      = "post_abc123";
    private static final String LIKE_ID      = "like_def789";
    private static final String COMMENT_ID   = "comment_def456";
    private static final String ACTOR_NICKNAME = "새벽러너";

    // ── POST_LIKED Envelope ───────────────────────────────────────────────────
    private static final String POST_LIKED_JSON = """
            {
              "eventId": "evt-001",
              "eventType": "POST_LIKED",
              "occurredAt": "2026-06-16T05:30:00Z",
              "payload": {
                "postId": "post_abc123",
                "likeId": "like_def789",
                "userId": "7b9f6e22-03e7-4b59-a9a4-95de4e2f1234",
                "actorNickname": "새벽러너",
                "ownerId": "1caa0424-5b1e-4f8a-90b5-bc1d6e2a5678",
                "likedAt": "2026-06-16T06:00:00Z"
              }
            }
            """;

    // ── POST_COMMENTED Envelope ───────────────────────────────────────────────
    private static final String POST_COMMENTED_JSON = """
            {
              "eventId": "evt-002",
              "eventType": "POST_COMMENTED",
              "occurredAt": "2026-06-16T06:10:00Z",
              "payload": {
                "postId": "post_abc123",
                "commentId": "comment_def456",
                "userId": "7b9f6e22-03e7-4b59-a9a4-95de4e2f1234",
                "actorNickname": "새벽러너",
                "ownerId": "1caa0424-5b1e-4f8a-90b5-bc1d6e2a5678",
                "commentedAt": "2026-06-16T06:10:00Z"
              }
            }
            """;

    // ── 1. POST_LIKED → handler.handlePostEvent 호출 + command 필드 검증 ──────

    @Test
    @DisplayName("POST_LIKED Envelope: likeId를 targetId로, userId를 actorId로 변환해 handler를 호출한다")
    void consume_postLikedEnvelope_callsHandlerWithCorrectCommand() throws Exception {
        // When
        consumer.consume(POST_LIKED_JSON);

        // Then
        ArgumentCaptor<PostNotificationCommand> captor =
                ArgumentCaptor.forClass(PostNotificationCommand.class);
        verify(handler).handlePostEvent(captor.capture());

        PostNotificationCommand cmd = captor.getValue();
        assertThat(cmd.eventType()).isEqualTo("POST_LIKED");
        assertThat(cmd.targetId()).isEqualTo(LIKE_ID);         // likeId → targetId
        assertThat(cmd.postId()).isEqualTo(POST_ID);
        assertThat(cmd.actorId()).isEqualTo(UUID.fromString(ACTOR_UUID));  // userId → actorId
        assertThat(cmd.actorNickname()).isEqualTo(ACTOR_NICKNAME);
        assertThat(cmd.ownerId()).isEqualTo(UUID.fromString(OWNER_UUID));
    }

    // ── 2. POST_COMMENTED → handler.handlePostEvent 호출 + command 필드 검증 ──

    @Test
    @DisplayName("POST_COMMENTED Envelope: commentId를 targetId로, userId를 actorId로 변환해 handler를 호출한다")
    void consume_postCommentedEnvelope_callsHandlerWithCorrectCommand() throws Exception {
        // When
        consumer.consume(POST_COMMENTED_JSON);

        // Then
        ArgumentCaptor<PostNotificationCommand> captor =
                ArgumentCaptor.forClass(PostNotificationCommand.class);
        verify(handler).handlePostEvent(captor.capture());

        PostNotificationCommand cmd = captor.getValue();
        assertThat(cmd.eventType()).isEqualTo("POST_COMMENTED");
        assertThat(cmd.targetId()).isEqualTo(COMMENT_ID);      // commentId → targetId
        assertThat(cmd.postId()).isEqualTo(POST_ID);
        assertThat(cmd.actorId()).isEqualTo(UUID.fromString(ACTOR_UUID));
        assertThat(cmd.actorNickname()).isEqualTo(ACTOR_NICKNAME);
        assertThat(cmd.ownerId()).isEqualTo(UUID.fromString(OWNER_UUID));
    }

    // ── 3. tolerant reader: Envelope·payload 모두 unknown 필드 포함 시 파싱 성공 ──

    @Test
    @DisplayName("Envelope과 payload에 계약에 없는 unknown 필드가 있어도 파싱에 실패하지 않고 handler를 호출한다")
    void consume_envelopeWithUnknownFields_parsesSuccessfullyAndCallsHandler() throws Exception {
        String jsonWithUnknownFields = """
                {
                  "eventId": "evt-003",
                  "eventType": "POST_LIKED",
                  "occurredAt": "2026-06-16T05:30:00Z",
                  "newEnvelopeField": "future_value",
                  "payload": {
                    "postId": "post_abc123",
                    "likeId": "like_def789",
                    "userId": "7b9f6e22-03e7-4b59-a9a4-95de4e2f1234",
                    "actorNickname": "새벽러너",
                    "ownerId": "1caa0424-5b1e-4f8a-90b5-bc1d6e2a5678",
                    "likedAt": "2026-06-16T06:00:00Z",
                    "unknownPayloadField": 9999,
                    "futureFlag": true
                  }
                }
                """;

        // When / Then: 예외 없이 handler 호출 확인
        consumer.consume(jsonWithUnknownFields);
        verify(handler).handlePostEvent(org.mockito.ArgumentMatchers.any(PostNotificationCommand.class));
    }

    // ── 4. JSON 파싱 불가 문자열 → 예외 throw (Consumer 재시도 정책 유도) ─────

    @Test
    @DisplayName("JSON 파싱 불가 문자열 수신 시 예외를 던져 Consumer 재시도 정책을 태운다")
    void consume_invalidJson_throwsException() {
        assertThatThrownBy(() -> consumer.consume("{not-valid-json"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("빈 문자열 수신 시 예외를 던져 Consumer 재시도 정책을 태운다")
    void consume_emptyString_throwsException() {
        assertThatThrownBy(() -> consumer.consume(""))
                .isInstanceOf(Exception.class);
    }
}