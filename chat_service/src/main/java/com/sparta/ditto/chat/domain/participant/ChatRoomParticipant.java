package com.sparta.ditto.chat.domain.participant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(
        name = "chat_room_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_room_participants_room_id_user_id",
                columnNames = {"room_id", "user_id"}
        ),
        // 내 채팅방 목록 조회와 방별 현재 참여자 조회에 사용하는 인덱스다.
        indexes = {
                @Index(name = "idx_chat_room_participants_user_id_left_at", columnList = "user_id, left_at"),
                @Index(name = "idx_chat_room_participants_room_id_left_at", columnList = "room_id, left_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomParticipant {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ParticipantRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "last_read_message_id", length = 50)
    private String lastReadMessageId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "last_visible_message_id", length = 50)
    private String lastVisibleMessageId;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;

    private ChatRoomParticipant(UUID roomId, UUID userId, ParticipantRole role) {
        this.roomId = Objects.requireNonNull(roomId, "roomId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.hidden = false;
        this.notificationEnabled = true;
    }

    public static ChatRoomParticipant join(UUID roomId, UUID userId, ParticipantRole role) {
        return new ChatRoomParticipant(roomId, userId, role);
    }

    public void leave(String lastVisibleMessageId) {
        this.leftAt = Instant.now();
        this.lastVisibleMessageId = lastVisibleMessageId;
    }

    public void rejoin() {
        this.leftAt = null;
        this.hidden = false;
    }

    public void updateLastRead(String lastReadMessageId, Instant lastReadAt) {
        this.lastReadMessageId = Objects.requireNonNull(lastReadMessageId, "lastReadMessageId must not be null");
        this.lastReadAt = Objects.requireNonNull(lastReadAt, "lastReadAt must not be null");
    }

    public void hide() {
        this.hidden = true;
    }

    public void changeNotificationEnabled(boolean enabled) {
        this.notificationEnabled = enabled;
    }

    @PrePersist
    void prePersist() {
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
    }
}
