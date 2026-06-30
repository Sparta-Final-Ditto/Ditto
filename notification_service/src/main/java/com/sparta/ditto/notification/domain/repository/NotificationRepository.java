package com.sparta.ditto.notification.domain.repository;

import com.sparta.ditto.notification.domain.entity.Notification;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);
    Optional<Notification> findByIdAndReceiverId(UUID id, UUID receiverId);
}