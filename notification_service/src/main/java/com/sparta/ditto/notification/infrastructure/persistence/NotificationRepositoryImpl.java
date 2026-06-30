package com.sparta.ditto.notification.infrastructure.persistence;

import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public Optional<Notification> findByIdAndReceiverId(UUID id, UUID receiverId) {
        return jpaRepository.findByIdAndReceiverIdAndDeletedAtIsNull(id, receiverId);
    }

    @Override
    public List<Notification> findNotificationsWithCursor(
            UUID receiverId, Instant cursorCreatedAt, UUID cursorId, int limit) {
        return jpaRepository.findNotificationsWithCursor(
                receiverId, cursorCreatedAt, cursorId, PageRequest.of(0, limit));
    }

    @Override
    public long countUnreadByReceiverId(UUID receiverId) {
        return jpaRepository.countUnreadByReceiverId(receiverId);
    }

    @Override
    public List<Notification> findUnreadChatByReceiverId(UUID receiverId) {
        return jpaRepository.findUnreadByReceiverIdAndType(receiverId, NotificationType.CHAT_MESSAGE);
    }
}