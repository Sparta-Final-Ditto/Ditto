package com.sparta.ditto.notification.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    public Map<String, Long> countUnreadChatByRoomIds(UUID receiverId, Set<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return Map.of();
        }
        List<Notification> unreadChats = jpaRepository.findUnreadByReceiverIdAndType(
                receiverId, NotificationType.CHAT_MESSAGE);

        Map<String, Long> result = new HashMap<>();
        for (Notification notification : unreadChats) {
            String roomId = extractRoomId(notification.getMetaData());
            if (roomId != null && roomIds.contains(roomId)) {
                result.merge(roomId, 1L, Long::sum);
            }
        }
        return result;
    }

    private String extractRoomId(String metaData) {
        if (metaData == null || metaData.isBlank()) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(metaData);
            JsonNode roomIdNode = node.get("roomId");
            return (roomIdNode != null && !roomIdNode.isNull()) ? roomIdNode.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
