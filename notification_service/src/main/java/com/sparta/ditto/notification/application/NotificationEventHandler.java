package com.sparta.ditto.notification.application;

import com.sparta.ditto.notification.application.dto.ChatNotificationCommand;
import com.sparta.ditto.notification.application.dto.PostNotificationCommand;
import com.sparta.ditto.notification.application.port.MetaDataPort;
import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationEventHandler {

    private static final Set<String> SUBSCRIBED_POST_TYPES = Set.of("POST_LIKED", "POST_COMMENTED");

    private final NotificationRepository notificationRepository;
    private final MetaDataPort metaDataPort;

    @Transactional
    public void handlePostEvent(PostNotificationCommand cmd) {
        if (!SUBSCRIBED_POST_TYPES.contains(cmd.eventType())) {
            return;
        }
        validatePostCommand(cmd);
        if (cmd.actorId().equals(cmd.ownerId())) {
            return;
        }

        boolean isLike = "POST_LIKED".equals(cmd.eventType());
        NotificationType type = isLike ? NotificationType.LIKE : NotificationType.COMMENT;
        TargetType targetType = isLike ? TargetType.LIKE : TargetType.COMMENT;
        String message = isLike
                ? cmd.actorNickname() + "님이 좋아요를 눌렀습니다."
                : cmd.actorNickname() + "님이 댓글을 남겼습니다.";

        Notification notification = Notification.create(
                cmd.ownerId(), cmd.actorId(), type, targetType,
                cmd.targetId(), message, metaDataPort.buildPostMetaData(cmd.postId()));
        notificationRepository.save(notification);
    }

    @Transactional
    public void handleChatMessage(ChatNotificationCommand cmd) {
        if (cmd.receiverIds() == null) {
            throw new IllegalArgumentException("receiverIds는 null일 수 없습니다.");
        }
        if (cmd.messageId() == null) {
            throw new IllegalArgumentException("messageId는 null일 수 없습니다.");
        }
        if (cmd.roomId() == null) {
            throw new IllegalArgumentException("roomId는 null일 수 없습니다.");
        }
        if (cmd.senderNickname() == null) {
            throw new IllegalArgumentException("senderNickname은 null일 수 없습니다.");
        }
        if (cmd.preview() == null || cmd.preview().isBlank()) {
            throw new IllegalArgumentException("preview는 null/blank일 수 없습니다.");
        }
        if (cmd.receiverIds().isEmpty()) {
            return;
        }

        String metaData = metaDataPort.buildChatMetaData(
                cmd.roomId(), cmd.senderNickname(), cmd.senderProfileImageUrl());
        for (UUID receiverId : cmd.receiverIds()) {
            Notification notification = Notification.create(
                    receiverId, cmd.senderId(), NotificationType.CHAT_MESSAGE, TargetType.CHAT_MESSAGE,
                    cmd.messageId(), cmd.preview(), metaData);
            notificationRepository.save(notification);
        }
    }

    private static void validatePostCommand(PostNotificationCommand cmd) {
        if (cmd.actorId() == null) {
            throw new IllegalArgumentException("actorId는 null일 수 없습니다.");
        }
        if (cmd.ownerId() == null) {
            throw new IllegalArgumentException("ownerId는 null일 수 없습니다.");
        }
        if (cmd.actorNickname() == null) {
            throw new IllegalArgumentException("actorNickname은 null일 수 없습니다.");
        }
        if (cmd.targetId() == null) {
            throw new IllegalArgumentException("targetId는 null일 수 없습니다.");
        }
        if (cmd.postId() == null) {
            throw new IllegalArgumentException("postId는 null일 수 없습니다.");
        }
    }
}