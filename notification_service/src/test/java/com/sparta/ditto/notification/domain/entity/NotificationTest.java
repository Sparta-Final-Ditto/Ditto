package com.sparta.ditto.notification.domain.entity;

import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final UUID RECEIVER_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final String TARGET_ID = UUID.randomUUID().toString();
    private static final String MESSAGE = "새벽러너님이 좋아요를 눌렀습니다.";
    private static final String META_DATA = "{\"postId\":\"post_abc\"}";

    @Test
    @DisplayName("정적 팩토리로 생성 시 모든 필드가 올바르게 세팅된다")
    void create_setsAllFields() {
        // Given / When
        Notification notification = Notification.create(
                RECEIVER_ID,
                ACTOR_ID,
                NotificationType.LIKE,
                TargetType.LIKE,
                TARGET_ID,
                MESSAGE,
                META_DATA
        );

        // Then
        assertThat(notification.getReceiverId()).isEqualTo(RECEIVER_ID);
        assertThat(notification.getActorId()).isEqualTo(ACTOR_ID);
        assertThat(notification.getType()).isEqualTo(NotificationType.LIKE);
        assertThat(notification.getTargetType()).isEqualTo(TargetType.LIKE);
        assertThat(notification.getTargetId()).isEqualTo(TARGET_ID);
        assertThat(notification.getMessage()).isEqualTo(MESSAGE);
        assertThat(notification.getMetaData()).isEqualTo(META_DATA);
    }

    @Test
    @DisplayName("정적 팩토리로 생성 시 isRead는 기본값 false이다")
    void create_isReadDefaultFalse() {
        // Given / When
        Notification notification = Notification.create(
                RECEIVER_ID,
                ACTOR_ID,
                NotificationType.LIKE,
                TargetType.LIKE,
                TARGET_ID,
                MESSAGE,
                META_DATA
        );

        // Then
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    @DisplayName("read() 호출 시 isRead가 true로 변경된다")
    void read_changesIsReadToTrue() {
        // Given
        Notification notification = Notification.create(
                RECEIVER_ID,
                ACTOR_ID,
                NotificationType.LIKE,
                TargetType.LIKE,
                TARGET_ID,
                MESSAGE,
                META_DATA
        );

        // When
        notification.read();

        // Then
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("이미 읽은 알림에 read()를 재호출해도 isRead는 true를 유지한다")
    void read_alreadyRead_remainsTrue() {
        // Given
        Notification notification = Notification.create(
                RECEIVER_ID,
                ACTOR_ID,
                NotificationType.COMMENT,
                TargetType.LIKE,
                TARGET_ID,
                MESSAGE,
                META_DATA
        );
        notification.read();

        // When
        notification.read();

        // Then
        assertThat(notification.isRead()).isTrue();
    }

    // ── type/target_type 불변식 ────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    @DisplayName("NotificationType의 모든 값(LIKE, COMMENT, CHAT_MESSAGE)으로 알림을 생성할 수 있다")
    void create_withAllNotificationTypes_succeeds(NotificationType type) {
        // Given / When
        TargetType targetType = TargetType.valueOf(type.name());
        Notification notification = Notification.create(
                RECEIVER_ID, ACTOR_ID, type, targetType, TARGET_ID, MESSAGE, META_DATA);

        // Then
        assertThat(notification.getType()).isEqualTo(type);
        assertThat(notification.getTargetType()).isEqualTo(targetType);
    }

    // ── target_id 타입 불변식 ─────────────────────────────────────────────

    @Test
    @DisplayName("target_id는 String 타입으로 저장된다 (likeId/commentId/messageId 등 여러 서비스 리소스 ID 수용)")
    void create_targetId_isStoredAsString() {
        // Given
        String likeId = UUID.randomUUID().toString();

        // When
        Notification notification = Notification.create(
                RECEIVER_ID, ACTOR_ID, NotificationType.LIKE, TargetType.LIKE, likeId, MESSAGE, META_DATA);

        // Then
        assertThat(notification.getTargetId()).isEqualTo(likeId);
        assertThat(notification.getTargetId()).isInstanceOf(String.class);
    }

    // ── actor_id NOT NULL 불변식 ──────────────────────────────────────────

    @Test
    @DisplayName("actorId가 null이면 IllegalArgumentException을 던진다")
    void create_withNullActorId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Notification.create(
                RECEIVER_ID, null, NotificationType.LIKE, TargetType.LIKE, TARGET_ID, MESSAGE, META_DATA
        )).isInstanceOf(IllegalArgumentException.class);
    }

}