package com.sparta.ditto.notification.infrastructure.persistence;

import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import java.time.Instant;
import java.util.Collection;
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

    /**
     * 호출자(Recorder)의 트랜잭션에 참여하는 단일 저장. "이벤트 1건 = 1커밋"(TRD 11.2) 원칙을 위해
     * 루프를 트랜잭션별로 쪼개지 않는다. 멱등 처리는 저장 전 사전 exists 체크(Recorder)가 담당하며,
     * 여기서 DataIntegrityViolationException을 catch하지 않는다(PostgreSQL은 제약 위반 시 트랜잭션이
     * abort되어 catch 후 진행이 불가능하므로 catch-continue는 금지 — TRD 10장).
     * saveAndFlush로 즉시 flush하여 동일 트랜잭션 내 후속 조회에 반영되도록 한다.
     */
    @Override
    public Notification save(Notification notification) {
        return jpaRepository.saveAndFlush(notification);
    }

    @Override
    public boolean existsByTypeAndTargetIdAndReceiverId(
            NotificationType type, String targetId, UUID receiverId) {
        return jpaRepository.existsByTypeAndTargetIdAndReceiverId(type, targetId, receiverId);
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
        return jpaRepository.findUnreadByReceiverIdAndType(
                receiverId, NotificationType.CHAT_MESSAGE);
    }

    @Override
    public int markAsReadByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return jpaRepository.bulkMarkAsRead(ids);
    }
}
