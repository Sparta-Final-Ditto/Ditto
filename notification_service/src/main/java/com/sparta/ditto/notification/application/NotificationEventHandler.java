package com.sparta.ditto.notification.application;

import com.sparta.ditto.notification.application.dto.ChatNotificationCommand;
import com.sparta.ditto.notification.application.dto.PostNotificationCommand;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Kafka 이벤트 처리 진입점. @Transactional을 두지 않으며,
 * 구독 타입 필터 / 자기 알림 skip / 계약 검증만 수행한 뒤,
 * 저장 트랜잭션은 별도 빈 NotificationRecorder에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class NotificationEventHandler {

    private static final Set<String> SUBSCRIBED_POST_TYPES = Set.of("POST_LIKED", "POST_COMMENTED");

    private final NotificationRecorder notificationRecorder;

    public void handlePostEvent(PostNotificationCommand cmd) {
        if (!SUBSCRIBED_POST_TYPES.contains(cmd.eventType())) {
            return;
        }
        validatePostCommand(cmd);
        if (cmd.actorId().equals(cmd.ownerId())) {
            return;
        }
        notificationRecorder.recordPost(cmd);
    }

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
        notificationRecorder.recordChat(cmd);
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
