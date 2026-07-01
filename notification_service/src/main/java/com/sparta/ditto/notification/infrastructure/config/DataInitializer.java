package com.sparta.ditto.notification.infrastructure.config;

import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final UUID RECEIVER_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID ACTOR_A =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID ACTOR_B =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");

    private final NotificationRepository notificationRepository;

    @Override
    public void run(String... args) {
        // LIKE 알림 (미읽음) — roomUnreadCount 없음
        notificationRepository.save(Notification.create(
                RECEIVER_ID, ACTOR_A,
                NotificationType.LIKE, TargetType.POST,
                "post-001",
                "러너님이 좋아요를 눌렀습니다.",
                "{\"postId\":\"post-001\"}"
        ));

        // COMMENT 알림 (미읽음) — roomUnreadCount 없음
        Notification comment = Notification.create(
                RECEIVER_ID, ACTOR_B,
                NotificationType.COMMENT, TargetType.POST,
                "post-001",
                "주원님이 댓글을 남겼습니다.",
                "{\"postId\":\"post-001\"}"
        );
        notificationRepository.save(comment);

        // CHAT_MESSAGE 알림 3개 (같은 room, 미읽음) — roomUnreadCount=3
        for (int i = 1; i <= 3; i++) {
            notificationRepository.save(Notification.create(
                    RECEIVER_ID, ACTOR_A,
                    NotificationType.CHAT_MESSAGE, TargetType.CHAT_MESSAGE,
                    "msg-00" + i,
                    "메시지 " + i,
                    "{\"roomId\":\"room-001\",\"senderNickname\":\"러너\",\"senderProfileImageUrl\":null}"
            ));
        }

        // 이미 읽은 알림 — unreadCount에 미포함
        Notification read = Notification.create(
                RECEIVER_ID, ACTOR_B,
                NotificationType.LIKE, TargetType.POST,
                "post-002",
                "주원님이 좋아요를 눌렀습니다.",
                "{\"postId\":\"post-002\"}"
        );
        read.read();
        notificationRepository.save(read);

        log.info("[DataInitializer] 더미 알림 6건 저장 완료 (receiver: {})", RECEIVER_ID);
    }
}