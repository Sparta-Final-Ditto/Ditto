package com.sparta.ditto.chat.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

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
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "user1_id", nullable = false)
    private UUID user1Id;

    @Column(name = "user2_id", nullable = false)
    private UUID user2Id;

    private DirectChatPair(UUID roomId, UUID user1Id, UUID user2Id) {
        this.roomId = Objects.requireNonNull(roomId, "roomId must not be null");
        this.user1Id = Objects.requireNonNull(user1Id, "user1Id must not be null");
        this.user2Id = Objects.requireNonNull(user2Id, "user2Id must not be null");
    }

    public static DirectChatPair create(UUID roomId, UUID userAId, UUID userBId) {
        OrderedUserIds orderedUserIds = orderUserIds(userAId, userBId);
        return new DirectChatPair(roomId, orderedUserIds.user1Id(), orderedUserIds.user2Id());
    }

    public static OrderedUserIds orderUserIds(UUID userAId, UUID userBId) {
        UUID firstUserId = Objects.requireNonNull(userAId, "userAId must not be null");
        UUID secondUserId = Objects.requireNonNull(userBId, "userBId must not be null");
        if (firstUserId.equals(secondUserId)) {
            throw new IllegalArgumentException("direct chat users must be different");
        }
        if (firstUserId.compareTo(secondUserId) > 0) {
            return new OrderedUserIds(secondUserId, firstUserId);
        }
        return new OrderedUserIds(firstUserId, secondUserId);
    }

    public record OrderedUserIds(UUID user1Id, UUID user2Id) {
    }
}
