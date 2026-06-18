package com.sparta.ditto.chat.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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
        name = "chat_rooms",
        indexes = @Index(
                name = "idx_chat_rooms_last_message_at",
                columnList = "last_message_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    @Column(name = "room_name", length = 100)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    // 서버 시간대 차이를 피하기 위해 서비스 시각은 Instant로 저장한다.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 메시지 ID는 chat-service가 생성한 UUID v7/ULID 문자열이며 MongoDB _id로 저장된다.
    @Column(name = "last_message_id", length = 50)
    private String lastMessageId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "inactivated_at")
    private Instant inactivatedAt;

    @Column(name = "inactivated_by")
    private UUID inactivatedBy;

    private ChatRoom(RoomType roomType, String roomName, UUID createdBy) {
        this.roomType = Objects.requireNonNull(roomType, "roomType must not be null");
        this.roomName = roomName;
        this.status = RoomStatus.ACTIVE;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
    }

    public static ChatRoom createDirect(UUID createdBy) {
        return new ChatRoom(RoomType.DIRECT, null, createdBy);
    }

    public static ChatRoom createGroup(String roomName, UUID createdBy) {
        if (roomName == null || roomName.isBlank()) {
            throw new IllegalArgumentException("roomName must not be blank");
        }
        return new ChatRoom(RoomType.GROUP, roomName, createdBy);
    }

    public void updateLastMessage(String messageId, Instant messageCreatedAt) {
        this.lastMessageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.lastMessageAt = Objects.requireNonNull(
                messageCreatedAt,
                "messageCreatedAt must not be null"
        );
    }

    public void inactivate(UUID userId) {
        this.status = RoomStatus.INACTIVE;
        this.inactivatedBy = Objects.requireNonNull(userId, "userId must not be null");
        this.inactivatedAt = Instant.now();
    }

    public void reactivate() {
        this.status = RoomStatus.ACTIVE;
        // 재활성화된 방은 현재 활성 상태이므로 비활성화 메타데이터를 초기화한다.
        this.inactivatedBy = null;
        this.inactivatedAt = null;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = RoomStatus.ACTIVE;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
