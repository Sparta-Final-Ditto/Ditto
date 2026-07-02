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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    /**
     * NOT_SUPPORTED: 상위 트랜잭션을 suspend한 뒤 jpaRepository가 독립 트랜잭션을 생성·커밋·롤백.
     * 중복 위반 시 DataIntegrityViolationException을 catch 후 skip — UnexpectedRollbackException 없이
     * 상위 트랜잭션(핸들러)에 전파하지 않아 Consumer 불필요 재시도를 방지한다.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Notification save(Notification notification) {
        try {
            return jpaRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException e) {
            log.info("중복 알림 skip (멱등 처리): type={}, targetId={}, receiverId={}",
                    notification.getType(), notification.getTargetId(), notification.getReceiverId());
            return notification;
        }
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

    @Override
    public int markAsReadByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return jpaRepository.bulkMarkAsRead(ids);
    }
}
