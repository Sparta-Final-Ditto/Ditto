package com.sparta.ditto.notification.application;

import com.sparta.ditto.notification.application.dto.NotificationItemResult;
import com.sparta.ditto.notification.application.dto.NotificationListResult;
import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private static final UUID USER_ID = UUID.randomUUID();

    private static Notification mockNotification(NotificationType type, String metaData) {
        Notification n = org.mockito.Mockito.mock(Notification.class);
        when(n.getId()).thenReturn(UUID.randomUUID());
        when(n.getType()).thenReturn(type);
        when(n.getActorId()).thenReturn(UUID.randomUUID());
        when(n.getTargetType()).thenReturn(
                type == NotificationType.CHAT_MESSAGE ? TargetType.CHAT_MESSAGE : TargetType.POST);
        when(n.getTargetId()).thenReturn("target");
        when(n.getMessage()).thenReturn("message");
        when(n.isRead()).thenReturn(false);
        when(n.getMetaData()).thenReturn(metaData);
        when(n.getCreatedAt()).thenReturn(Instant.now());
        return n;
    }

    // ── hasNext/nextCursor 경계 ───────────────────────────────────────────────

    @Test
    @DisplayName("정확히 size개 반환 시 hasNext=false, nextCursor=null")
    void getNotifications_정확히size개_hasNext_false() {
        // given
        int size = 2;
        List<Notification> fetched = List.of(
                mockNotification(NotificationType.LIKE, null),
                mockNotification(NotificationType.LIKE, null)
        );
        when(notificationRepository.findNotificationsWithCursor(
                eq(USER_ID), isNull(), isNull(), eq(size + 1)))
                .thenReturn(fetched);
        when(notificationRepository.countUnreadByReceiverId(USER_ID)).thenReturn(0L);

        // when
        NotificationListResult result = notificationService.getNotifications(USER_ID, null, size);

        // then
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.notifications()).hasSize(2);
    }

    @Test
    @DisplayName("size+1개 반환 시 hasNext=true, nextCursor=응답 마지막 항목 id")
    void getNotifications_size플러스1개_hasNext_true_nextCursor정확() {
        // given
        int size = 2;
        Notification first = mockNotification(NotificationType.LIKE, null);
        Notification second = mockNotification(NotificationType.LIKE, null);
        Notification third = org.mockito.Mockito.mock(Notification.class); // trim되는 초과분, 내부 처리 없음
        when(notificationRepository.findNotificationsWithCursor(
                eq(USER_ID), isNull(), isNull(), eq(size + 1)))
                .thenReturn(List.of(first, second, third));
        when(notificationRepository.countUnreadByReceiverId(USER_ID)).thenReturn(0L);

        // when
        NotificationListResult result = notificationService.getNotifications(USER_ID, null, size);

        // then
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(second.getId()); // 응답 마지막(2번째) 항목
        assertThat(result.notifications()).hasSize(2);
    }

    // ── unreadCount 매핑 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("unreadCount는 repository 조회 값을 그대로 반환한다")
    void getNotifications_unreadCount_매핑() {
        // given
        when(notificationRepository.findNotificationsWithCursor(
                eq(USER_ID), isNull(), isNull(), anyInt()))
                .thenReturn(List.of());
        when(notificationRepository.countUnreadByReceiverId(USER_ID)).thenReturn(7L);

        // when
        NotificationListResult result = notificationService.getNotifications(USER_ID, null, 20);

        // then
        assertThat(result.unreadCount()).isEqualTo(7L);
    }

    // ── roomUnreadCount: 채팅 포함, 비채팅 null ───────────────────────────────

    @Test
    @DisplayName("CHAT_MESSAGE 알림에는 roomUnreadCount가 포함되고, 비채팅 알림에는 null이다")
    void getNotifications_채팅_roomUnreadCount_비채팅_null() {
        // given
        String room1Meta = "{\"roomId\":\"room1\",\"senderNickname\":\"A\",\"senderProfileImageUrl\":null}";
        Notification likeNotification = mockNotification(NotificationType.LIKE, null);
        Notification chatNotification = mockNotification(NotificationType.CHAT_MESSAGE, room1Meta);

        // 전체 미읽음 채팅 목록: room1 알림 3개 (페이지 무관)
        Notification unreadChat1 = mock(Notification.class);
        when(unreadChat1.getMetaData()).thenReturn(room1Meta);
        Notification unreadChat2 = mock(Notification.class);
        when(unreadChat2.getMetaData()).thenReturn(room1Meta);
        Notification unreadChat3 = mock(Notification.class);
        when(unreadChat3.getMetaData()).thenReturn(room1Meta);

        when(notificationRepository.findNotificationsWithCursor(
                eq(USER_ID), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(likeNotification, chatNotification));
        when(notificationRepository.countUnreadByReceiverId(USER_ID)).thenReturn(0L);
        when(notificationRepository.findUnreadChatByReceiverId(USER_ID))
                .thenReturn(List.of(unreadChat1, unreadChat2, unreadChat3));

        // when
        NotificationListResult result = notificationService.getNotifications(USER_ID, null, 20);

        // then
        NotificationItemResult likeResult = result.notifications().get(0);
        NotificationItemResult chatResult = result.notifications().get(1);
        assertThat(likeResult.roomUnreadCount()).isNull();
        assertThat(chatResult.roomUnreadCount()).isEqualTo(3L);
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"LIKE", "COMMENT"})
    @DisplayName("LIKE, COMMENT 알림은 roomUnreadCount가 항상 null이다")
    void getNotifications_비채팅_타입_roomUnreadCount_null(NotificationType type) {
        // given
        Notification notification = mockNotification(type, null);
        when(notificationRepository.findNotificationsWithCursor(
                eq(USER_ID), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(notification));
        when(notificationRepository.countUnreadByReceiverId(USER_ID)).thenReturn(0L);

        // when
        NotificationListResult result = notificationService.getNotifications(USER_ID, null, 20);

        // then
        assertThat(result.notifications().get(0).roomUnreadCount()).isNull();
        verify(notificationRepository, never()).findUnreadChatByReceiverId(any());
    }

    // ── getUnreadCount ────────────────────────────────────────────────────────

    @Test
    @DisplayName("미읽음 알림이 N건이면 N을 반환한다")
    void getUnreadCount_미읽음존재_N반환() {
        // given
        when(notificationRepository.countUnreadByReceiverId(USER_ID)).thenReturn(5L);

        // when
        long result = notificationService.getUnreadCount(USER_ID);

        // then
        assertThat(result).isEqualTo(5L);
    }

    @Test
    @DisplayName("미읽음 알림이 없으면 0을 반환한다")
    void getUnreadCount_미읽음없음_0반환() {
        // given
        when(notificationRepository.countUnreadByReceiverId(USER_ID)).thenReturn(0L);

        // when
        long result = notificationService.getUnreadCount(USER_ID);

        // then
        assertThat(result).isEqualTo(0L);
    }

    // ── cursor fallback ───────────────────────────────────────────────────────

    @Test
    @DisplayName("cursor 알림이 존재하지 않으면 첫 페이지(cursor=null)로 fallback한다")
    void getNotifications_cursor없으면_firstPage_fallback() {
        // given
        UUID unknownCursorId = UUID.randomUUID();
        when(notificationRepository.findByIdAndReceiverId(unknownCursorId, USER_ID))
                .thenReturn(Optional.empty());
        when(notificationRepository.findNotificationsWithCursor(
                eq(USER_ID), isNull(), isNull(), anyInt()))
                .thenReturn(List.of());
        when(notificationRepository.countUnreadByReceiverId(USER_ID)).thenReturn(0L);

        // when
        notificationService.getNotifications(USER_ID, unknownCursorId, 20);

        // then
        verify(notificationRepository)
                .findNotificationsWithCursor(USER_ID, null, null, 21);
    }
}
