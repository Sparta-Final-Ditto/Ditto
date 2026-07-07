package com.sparta.ditto.notification.application;

import com.sparta.ditto.notification.application.dto.NotificationItemResult;
import com.sparta.ditto.notification.application.dto.NotificationListResult;
import com.sparta.ditto.notification.application.dto.ReadByRoomResult;
import com.sparta.ditto.notification.application.dto.ReadNotificationResult;
import com.sparta.ditto.notification.application.port.MetaDataPort;
import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.exception.NotificationNotFoundException;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MetaDataPort metaDataPort;

    // ── 알림 목록 조회 ────
    public NotificationListResult getNotifications(UUID userId, UUID cursorId, int size) {
        Instant cursorCreatedAt = null;
        UUID resolvedCursorId = null;
        if (cursorId != null) {
            Optional<Notification> cursorNotification =
                    notificationRepository.findByIdAndReceiverId(cursorId, userId);
            if (cursorNotification.isPresent()) {
                cursorCreatedAt = cursorNotification.get().getCreatedAt();
                resolvedCursorId = cursorNotification.get().getId();
            }
        }

        List<Notification> fetched = notificationRepository.findNotificationsWithCursor(
                userId, cursorCreatedAt, resolvedCursorId, size + 1);

        boolean hasNext = fetched.size() > size;
        List<Notification> items = hasNext ? fetched.subList(0, size) : fetched;
        UUID nextCursor = hasNext ? items.get(items.size() - 1).getId() : null;

        long unreadCount = notificationRepository.countUnreadByReceiverId(userId);

        Map<UUID, String> notificationRoomIds = new LinkedHashMap<>();
        for (Notification n : items) {
            if (n.getType() == NotificationType.CHAT_MESSAGE) {
                String roomId = metaDataPort.extractRoomId(n.getMetaData());
                if (roomId != null) {
                    notificationRoomIds.put(n.getId(), roomId);
                }
            }
        }
        Map<String, Long> roomUnreadCounts = notificationRoomIds.isEmpty()
                ? Map.of()
                : notificationRepository.findUnreadChatByReceiverId(userId)
                        .stream()
                        .map(n -> metaDataPort.extractRoomId(n.getMetaData()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<NotificationItemResult> results = items.stream()
                .map(n -> {
                    Long roomUnreadCount = null;
                    if (n.getType() == NotificationType.CHAT_MESSAGE) {
                        String roomId = notificationRoomIds.get(n.getId());
                        roomUnreadCount = roomId != null
                                ? roomUnreadCounts.getOrDefault(roomId, 0L)
                                : null;
                    }
                    return NotificationItemResult.of(n, roomUnreadCount);
                })
                .toList();

        return NotificationListResult.of(unreadCount, results, nextCursor, hasNext);
    }

    // ── 알림 미읽음 수 조회 ────
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countUnreadByReceiverId(userId);
    }

    // ── 단건 알림 읽음 처리 ────
    @Transactional
    public ReadNotificationResult read(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndReceiverId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
        notification.read();
        return ReadNotificationResult.from(notification);
    }

    // ── 채팅방 단위 읽음 처리 ────
    @Transactional
    public ReadByRoomResult readByRoom(UUID userId, String roomId) {
        List<Notification> unreadChats = notificationRepository.findUnreadChatByReceiverId(userId);

        List<UUID> matchingIds = unreadChats.stream()
                .filter(n -> roomId.equals(metaDataPort.extractRoomId(n.getMetaData())))
                .map(Notification::getId)
                .toList();

        if (matchingIds.isEmpty()) {
            return ReadByRoomResult.of(roomId, 0);
        }

        int updatedCount = notificationRepository.markAsReadByIds(matchingIds);
        return ReadByRoomResult.of(roomId, updatedCount);
    }
}