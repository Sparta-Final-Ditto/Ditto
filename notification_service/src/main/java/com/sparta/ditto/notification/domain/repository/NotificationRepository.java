package com.sparta.ditto.notification.domain.repository;

import com.sparta.ditto.notification.domain.entity.Notification;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);
    Optional<Notification> findByIdAndReceiverId(UUID id, UUID receiverId);
    List<Notification> findNotificationsWithCursor(UUID receiverId, Instant cursorCreatedAt, UUID cursorId, int limit);
    long countUnreadByReceiverId(UUID receiverId);
    List<Notification> findUnreadChatByReceiverId(UUID receiverId);
    int markAsReadByIds(Collection<UUID> ids);
}

