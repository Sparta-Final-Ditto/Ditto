package com.sparta.ditto.chat.domain.room;

import com.sparta.ditto.common.entity.BaseEntity;
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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    @Column(name = "room_name", length = 100)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoomStatus status;

    // 메시지 ID는 chat-service가 생성한 UUID v7/ULID 문자열이며 MongoDB _id로 저장된다.
    @Column(name = "last_message_id", length = 50)
    private String lastMessageId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "inactivated_at")
    private Instant inactivatedAt;

    @Column(name = "inactivated_by")
    private UUID inactivatedBy;

    private ChatRoom(RoomType roomType, String roomName) {
        if (roomType == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.roomType = roomType;
        this.roomName = roomName;
        this.status = RoomStatus.ACTIVE;
    }

    public static ChatRoom createDirect() {
        return new ChatRoom(RoomType.DIRECT, null);
    }

    public static ChatRoom createGroup(String roomName) {
        if (roomName == null || roomName.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatRoom(RoomType.GROUP, roomName);
    }

    public void updateLastMessage(String messageId, Instant messageCreatedAt) {
        if (messageId == null || messageId.isBlank() || messageCreatedAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        // 재처리/비동기 유입으로 오래된 메시지가 뒤늦게 도착해도 최신 lastMessage를 덮지 않도록,
        // createdAt(같으면 messageId) 기준으로 더 최신일 때만 갱신한다.
        // 이미 로드된 필드끼리의 메모리 비교라 추가 DB 조회는 없다.
        if (!isNewerThanCurrentLastMessage(messageId, messageCreatedAt)) {
            return;
        }
        this.lastMessageId = messageId;
        this.lastMessageAt = messageCreatedAt;
    }

    private boolean isNewerThanCurrentLastMessage(String messageId, Instant messageCreatedAt) {
        if (this.lastMessageAt == null) {
            return true;
        }
        int createdAtCompare = messageCreatedAt.compareTo(this.lastMessageAt);
        if (createdAtCompare != 0) {
            return createdAtCompare > 0;
        }
        return this.lastMessageId == null || messageId.compareTo(this.lastMessageId) > 0;
    }

    public void inactivate(UUID userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.status = RoomStatus.INACTIVE;
        this.inactivatedBy = userId;
        this.inactivatedAt = Instant.now();
    }

    public void reactivate() {
        this.status = RoomStatus.ACTIVE;
        // 재활성화된 방은 현재 활성 상태이므로 비활성화 메타데이터를 초기화한다.
        this.inactivatedBy = null;
        this.inactivatedAt = null;
    }
}
