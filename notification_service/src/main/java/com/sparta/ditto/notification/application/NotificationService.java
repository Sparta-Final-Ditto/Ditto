package com.sparta.ditto.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.notification.application.dto.NotificationItemResult;
import com.sparta.ditto.notification.application.dto.NotificationListResult;
import com.sparta.ditto.notification.application.dto.ReadNotificationResult;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final NotificationRepository notificationRepository;

    // ── 알림 목록 조회 ────
    public NotificationListResult getNotifications(UUID userId, UUID cursorId, int size) {
        // cursor 해석: 없거나 삭제됐으면 첫 페이지 fallback
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

        // size+1 조회 → hasNext 판단
        List<Notification> fetched = notificationRepository.findNotificationsWithCursor(
                userId, cursorCreatedAt, resolvedCursorId, size + 1);

        boolean hasNext = fetched.size() > size;
        List<Notification> items = hasNext ? fetched.subList(0, size) : fetched;
        UUID nextCursor = hasNext ? items.get(items.size() - 1).getId() : null;

        long unreadCount = notificationRepository.countUnreadByReceiverId(userId);

        // 페이지 내 채팅 알림의 roomId를 파싱해 두고, 채팅 알림이 있을 때만 전체 미읽음 채팅 조회
        Map<UUID, String> notificationRoomIds = new LinkedHashMap<>();
        for (Notification n : items) {
            if (n.getType() == NotificationType.CHAT_MESSAGE) {
                String roomId = extractRoomId(n.getMetaData());
                if (roomId != null) {
                    notificationRoomIds.put(n.getId(), roomId);
                }
            }
        }
        Map<String, Long> roomUnreadCounts = notificationRoomIds.isEmpty()
                ? Map.of()
                : notificationRepository.findUnreadChatByReceiverId(userId)
                        .stream()
                        .map(n -> extractRoomId(n.getMetaData()))
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

    private static String extractRoomId(String metaData) {
        if (metaData == null || metaData.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(metaData).path("roomId").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    // ── 단건 알림 읽음 처리 ────
    @Transactional
    public ReadNotificationResult read(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndReceiverId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
        notification.read();
        return ReadNotificationResult.from(notification);
    }

}
