package com.sparta.ditto.notification.application.dto;

import java.util.UUID;

public record PostNotificationCommand(
        String eventType,
        String targetId,
        String postId,
        UUID actorId,
        String actorNickname,
        UUID ownerId
) {
    public static PostNotificationCommand of(
            String eventType, String targetId, String postId,
            UUID actorId, String actorNickname, UUID ownerId) {
        return new PostNotificationCommand(
                eventType, targetId, postId, actorId, actorNickname, ownerId);
    }
}
