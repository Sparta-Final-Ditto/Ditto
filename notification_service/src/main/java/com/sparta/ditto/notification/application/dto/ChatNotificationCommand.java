package com.sparta.ditto.notification.application.dto;

import java.util.List;
import java.util.UUID;

public record ChatNotificationCommand(
        String messageId,
        UUID senderId,
        String senderNickname,
        String senderProfileImageUrl,
        String roomId,
        List<UUID> receiverIds,
        String preview
) {
    public static ChatNotificationCommand of(
            String messageId, UUID senderId, String senderNickname,
            String senderProfileImageUrl, String roomId,
            List<UUID> receiverIds, String preview) {
        return new ChatNotificationCommand(
                messageId, senderId, senderNickname, senderProfileImageUrl,
                roomId, receiverIds, preview);
    }
}
