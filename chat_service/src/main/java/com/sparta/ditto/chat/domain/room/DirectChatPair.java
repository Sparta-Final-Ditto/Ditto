package com.sparta.ditto.chat.domain.room;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "direct_chat_pairs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_direct_chat_pairs_room_id", columnNames = "room_id"),
                @UniqueConstraint(
                        name = "uk_direct_chat_pairs_user1_id_user2_id",
                        columnNames = {"user1_id", "user2_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectChatPair {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "user1_id", nullable = false)
    private UUID user1Id;

    @Column(name = "user2_id", nullable = false)
    private UUID user2Id;

    private DirectChatPair(UUID roomId, UUID user1Id, UUID user2Id) {
        if (roomId == null || user1Id == null || user2Id == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        this.roomId = roomId;
        this.user1Id = user1Id;
        this.user2Id = user2Id;
    }

    public static DirectChatPair create(UUID roomId, UUID userAId, UUID userBId) {
        OrderedUserIds orderedUserIds = orderUserIds(userAId, userBId);
        return new DirectChatPair(roomId, orderedUserIds.user1Id(), orderedUserIds.user2Id());
    }

    public static OrderedUserIds orderUserIds(UUID userAId, UUID userBId) {
        if (userAId == null || userBId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        UUID firstUserId = userAId;
        UUID secondUserId = userBId;
        if (firstUserId.equals(secondUserId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (firstUserId.compareTo(secondUserId) > 0) {
            return new OrderedUserIds(secondUserId, firstUserId);
        }
        return new OrderedUserIds(firstUserId, secondUserId);
    }

    public record OrderedUserIds(UUID user1Id, UUID user2Id) {
    }
}
