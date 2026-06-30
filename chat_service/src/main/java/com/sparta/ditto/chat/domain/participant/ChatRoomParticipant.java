package com.sparta.ditto.chat.domain.participant;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
                @Index(
                        name = "idx_chat_room_participants_user_id_left_at",
                        columnList = "user_id, left_at"
                ),
                @Index(
                        name = "idx_chat_room_participants_room_id_left_at",
                        columnList = "room_id, left_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ParticipantRole role;

    @Column(name = "joined_at", nullable = false)
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
        if (roomId == null || userId == null || role == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.roomId = roomId;
        this.userId = userId;
        this.role = role;
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

    // 그룹방에서 나간 사용자를 재초대할 때 기존 row를 재사용한다.
    // left_at을 비우고 joined_at을 재참여 시각으로 갱신해 다시 활성 참여자로 만든다.
    public void reInvite(ParticipantRole role) {
        if (role == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.role = role;
        this.leftAt = null;
        this.joinedAt = Instant.now();
        this.hidden = false;
        this.notificationEnabled = true;
        this.lastReadMessageId = null;
        this.lastReadAt = null;
        this.lastVisibleMessageId = null;
    }

    public void updateLastRead(String lastReadMessageId, Instant lastReadAt) {
        if (lastReadMessageId == null || lastReadAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.lastReadMessageId = lastReadMessageId;
        this.lastReadAt = lastReadAt;
    }

    public void hide() {
        this.hidden = true;
    }

    public void changeNotificationEnabled(boolean enabled) {
        this.notificationEnabled = enabled;
    }

    public void assignOwnerRole() {
        this.role = ParticipantRole.OWNER;
    }

    @PrePersist
    void prePersist() {
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
    }
}
