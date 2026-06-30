package com.sparta.ditto.chat.application.event;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.room.port.ChatSenderProfile;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// notification-service로 전달되는 알림 이벤트
public record ChatMessageCreatedEvent(
        String eventId,
        String eventType,
        UUID roomId,
        RoomType roomType,
        String messageId,
        UUID senderId,
        String senderNickname,
        String senderProfileImageUrl,
        List<UUID> receiverIds,
        MessageType messageType,
        String preview,
        Instant createdAt
) {

    public static final String EVENT_TYPE = "CHAT_MESSAGE_CREATED";
    private static final int PREVIEW_MAX_LENGTH = 50;

    public static ChatMessageCreatedEvent of(
            SentMessage message,
            RoomType roomType,
            ChatSenderProfile senderProfile,
            List<UUID> receiverIds) {
        return new ChatMessageCreatedEvent(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                message.roomId(),
                roomType,
                message.messageId(),
                message.senderId(),
                senderProfile.nickname(),
                senderProfile.profileImageUrl(),
                List.copyOf(receiverIds),
                message.messageType(),
                preview(message.messageType(), message.content()),
                message.createdAt());
    }

    private static String preview(MessageType messageType, String content) {
        // IMAGE는 본문이 URL/메타데이터라 그대로 노출하지 않고 대체 문구로 전달한다.
        if (messageType == MessageType.IMAGE) {
            return "사진을 보냈습니다";
        }
        if (content == null) {
            return null;
        }
        return content.length() <= PREVIEW_MAX_LENGTH
                ? content
                : content.substring(0, PREVIEW_MAX_LENGTH);
    }
}
