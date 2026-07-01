package com.sparta.ditto.notification.infrastructure.persistence;

import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.type.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndReceiverIdAndDeletedAtIsNull(UUID id, UUID receiverId);

    @Query(value = """
            SELECT * FROM notifications
            WHERE receiver_id = CAST(:receiverId AS uuid)
              AND deleted_at IS NULL
              AND (
                CAST(:cursorCreatedAt AS timestamptz) IS NULL
                OR created_at < CAST(:cursorCreatedAt AS timestamptz)
                OR (created_at = CAST(:cursorCreatedAt AS timestamptz)
                    AND id < CAST(:cursorId AS uuid))
              )
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<Notification> findNotificationsWithCursor(
            @Param("receiverId") UUID receiverId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Query("SELECT COUNT(n) FROM Notification n "
            + "WHERE n.receiverId = :receiverId AND n.isRead = false AND n.deletedAt IS NULL")
    long countUnreadByReceiverId(@Param("receiverId") UUID receiverId);

    @Query("SELECT n FROM Notification n "
            + "WHERE n.receiverId = :receiverId AND n.type = :type "
            + "AND n.isRead = false AND n.deletedAt IS NULL")
    List<Notification> findUnreadByReceiverIdAndType(
            @Param("receiverId") UUID receiverId,
            @Param("type") NotificationType type
    );
}
