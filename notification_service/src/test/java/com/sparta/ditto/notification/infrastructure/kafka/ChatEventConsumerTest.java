package com.sparta.ditto.notification.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.notification.application.NotificationEventHandler;
import com.sparta.ditto.notification.application.dto.ChatNotificationCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatEventConsumer - chat-message-created flat 1단 파싱 → command 변환 → handler 위임")
class ChatEventConsumerTest {

    @Mock
    private NotificationEventHandler handler;

    private ChatEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ChatEventConsumer(new ObjectMapper().findAndRegisterModules(), handler);
    }

    // ── 공통 고정 값 ──────────────────────────────────────────────────────────
    private static final String MESSAGE_ID        = "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
    private static final String SENDER_UUID       = "7b9f6e22-03e7-4b59-a9a4-95de4e2f1234";
    private static final String SENDER_NICKNAME   = "주원";
    private static final String SENDER_PROFILE_URL = "https://cdn.example.com/profiles/juwon.png";
    private static final String ROOM_ID           = "3f6e9a62-6c3f-4ad2-9b2d-47a0e3d4b123";
    private static final String RECEIVER_1_UUID   = "1caa0424-5b1e-4f8a-90b5-bc1d6e2a5678";
    private static final String RECEIVER_2_UUID   = "c9bb8128-6cb7-44c1-a1a6-72a9e6f98765";
    private static final String PREVIEW           = "오늘 같이 공부하실래요?";

    // ── flat JSON (senderProfileImageUrl 포함) ────────────────────────────────
    private static final String CHAT_JSON_WITH_PROFILE = """
            {
              "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
              "eventType": "CHAT_MESSAGE_CREATED",
              "roomId": "3f6e9a62-6c3f-4ad2-9b2d-47a0e3d4b123",
              "roomType": "GROUP",
              "messageId": "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70",
              "senderId": "7b9f6e22-03e7-4b59-a9a4-95de4e2f1234",
              "senderNickname": "주원",
              "senderProfileImageUrl": "https://cdn.example.com/profiles/juwon.png",
              "receiverIds": [
                "1caa0424-5b1e-4f8a-90b5-bc1d6e2a5678",
                "c9bb8128-6cb7-44c1-a1a6-72a9e6f98765"
              ],
              "messageType": "TEXT",
              "preview": "오늘 같이 공부하실래요?",
              "createdAt": "2026-06-12T21:10:00"
            }
            """;

    // ── flat JSON (senderProfileImageUrl=null) ────────────────────────────────
    private static final String CHAT_JSON_NULL_PROFILE = """
            {
              "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
              "eventType": "CHAT_MESSAGE_CREATED",
              "roomId": "3f6e9a62-6c3f-4ad2-9b2d-47a0e3d4b123",
              "messageId": "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70",
              "senderId": "7b9f6e22-03e7-4b59-a9a4-95de4e2f1234",
              "senderNickname": "주원",
              "senderProfileImageUrl": null,
              "receiverIds": [
                "1caa0424-5b1e-4f8a-90b5-bc1d6e2a5678"
              ],
              "messageType": "TEXT",
              "preview": "사진을 보냈습니다"
            }
            """;

    // ── 1. flat 1단 파싱 → handler.handleChatMessage 호출 + command 필드 검증 ─

    @Test
    @DisplayName("chat-message-created flat JSON을 파싱해 ChatNotificationCommand로 변환 후 handler를 호출한다")
    void consume_flatJson_callsHandlerWithCorrectCommand() throws Exception {
        // When
        consumer.consume(CHAT_JSON_WITH_PROFILE);

        // Then
        ArgumentCaptor<ChatNotificationCommand> captor =
                ArgumentCaptor.forClass(ChatNotificationCommand.class);
        verify(handler).handleChatMessage(captor.capture());

        ChatNotificationCommand cmd = captor.getValue();
        assertThat(cmd.messageId()).isEqualTo(MESSAGE_ID);
        assertThat(cmd.senderId()).isEqualTo(UUID.fromString(SENDER_UUID));
        assertThat(cmd.senderNickname()).isEqualTo(SENDER_NICKNAME);
        assertThat(cmd.senderProfileImageUrl()).isEqualTo(SENDER_PROFILE_URL);
        assertThat(cmd.roomId()).isEqualTo(ROOM_ID);
        assertThat(cmd.receiverIds()).containsExactlyInAnyOrder(
                UUID.fromString(RECEIVER_1_UUID),
                UUID.fromString(RECEIVER_2_UUID));
        assertThat(cmd.preview()).isEqualTo(PREVIEW);
    }

    // ── 2. senderProfileImageUrl=null → command에 null로 전달 ─────────────────

    @Test
    @DisplayName("senderProfileImageUrl이 null이면 command.senderProfileImageUrl()도 null이다")
    void consume_nullProfileImageUrl_commandHasNullProfileUrl() throws Exception {
        // When
        consumer.consume(CHAT_JSON_NULL_PROFILE);

        // Then
        ArgumentCaptor<ChatNotificationCommand> captor =
                ArgumentCaptor.forClass(ChatNotificationCommand.class);
        verify(handler).handleChatMessage(captor.capture());

        assertThat(captor.getValue().senderProfileImageUrl()).isNull();
    }

    // ── 3. tolerant reader: unknown 필드 포함 시 파싱 성공 ────────────────────

    @Test
    @DisplayName("계약에 없는 unknown 필드가 포함된 JSON도 파싱에 실패하지 않고 handler를 호출한다")
    void consume_jsonWithUnknownFields_parsesSuccessfullyAndCallsHandler() throws Exception {
        String jsonWithUnknownFields = """
                {
                  "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                  "eventType": "CHAT_MESSAGE_CREATED",
                  "roomId": "3f6e9a62-6c3f-4ad2-9b2d-47a0e3d4b123",
                  "roomType": "GROUP",
                  "messageId": "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70",
                  "senderId": "7b9f6e22-03e7-4b59-a9a4-95de4e2f1234",
                  "senderNickname": "주원",
                  "senderProfileImageUrl": null,
                  "receiverIds": ["1caa0424-5b1e-4f8a-90b5-bc1d6e2a5678"],
                  "messageType": "TEXT",
                  "preview": "안녕하세요",
                  "futureField": "this_did_not_exist_before",
                  "anotherNewFlag": true,
                  "createdAt": "2026-06-12T21:10:00"
                }
                """;

        // When / Then: 예외 없이 handler 호출 확인
        consumer.consume(jsonWithUnknownFields);
        verify(handler).handleChatMessage(org.mockito.ArgumentMatchers.any(ChatNotificationCommand.class));
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