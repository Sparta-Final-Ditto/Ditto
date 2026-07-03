package com.sparta.ditto.notification.application;

import com.sparta.ditto.notification.application.dto.ChatNotificationCommand;
import com.sparta.ditto.notification.application.dto.PostNotificationCommand;
import com.sparta.ditto.notification.application.port.MetaDataPort;
import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 저장 트랜잭션 경계(별도 빈). NotificationEventHandler(진입, 트랜잭션 없음)가
 * 검증/skip 후 이 빈의 @Transactional 메서드를 호출해 저장을 위임한다.
 * 동일 클래스 self-invocation은 프록시를 우회하므로 반드시 별도 빈으로 둔다.
 */
@Service
@RequiredArgsConstructor
public class NotificationRecorder {

    private final NotificationRepository notificationRepository;
    private final MetaDataPort metaDataPort;

    @Transactional
    public void recordPost(PostNotificationCommand cmd) {
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
    public void recordChat(ChatNotificationCommand cmd) {
        String metaData = metaDataPort.buildChatMetaData(
                cmd.roomId(), cmd.senderNickname(), cmd.senderProfileImageUrl());
        for (UUID receiverId : cmd.receiverIds()) {
            Notification notification = Notification.create(
                    receiverId, cmd.senderId(), NotificationType.CHAT_MESSAGE, TargetType.CHAT_MESSAGE,
                    cmd.messageId(), cmd.preview(), metaData);
            notificationRepository.save(notification);
        }
    }
}