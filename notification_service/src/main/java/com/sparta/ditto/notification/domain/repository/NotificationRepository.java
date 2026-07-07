package com.sparta.ditto.notification.domain.repository;

import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.type.NotificationType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);
    boolean existsByTypeAndTargetIdAndReceiverId(NotificationType type, String targetId, UUID receiverId);
    Optional<Notification> findByIdAndReceiverId(UUID id, UUID receiverId);
    List<Notification> findNotificationsWithCursor(UUID receiverId, Instant cursorCreatedAt, UUID cursorId, int limit);
    long countUnreadByReceiverId(UUID receiverId);
    List<Notification> findUnreadChatByReceiverId(UUID receiverId);
    int markAsReadByIds(Collection<UUID> ids);
}

