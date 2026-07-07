package com.sparta.ditto.notification.application;

import com.sparta.ditto.notification.application.dto.NotificationItemResult;
import com.sparta.ditto.notification.application.dto.NotificationListResult;
import com.sparta.ditto.notification.application.port.MetaDataPort;
import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sparta.ditto.notification.application.dto.ReadByRoomResult;
import com.sparta.ditto.notification.application.dto.ReadNotificationResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MetaDataPort metaDataPort;

    @Captor
    private ArgumentCaptor<Collection<UUID>> idsCaptor;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUpMetaDataStubs() {
        lenient().when(metaDataPort.extractRoomId(anyString())).thenAnswer(inv -> {
            String meta = inv.getArgument(0);
            if (meta == null || meta.isBlank()) return null;
            int keyIdx = meta.indexOf("\"roomId\"");
            if (keyIdx < 0) return null;
            int colonIdx = meta.indexOf(':', keyIdx);
            if (colonIdx < 0) return null;
            int valStart = colonIdx + 1;
            while (valStart < meta.length() && Character.isWhitespace(meta.charAt(valStart))) valStart++;
            if (valStart >= meta.length() || meta.charAt(valStart) != '"') return null;
            valStart++;
            int valEnd = meta.indexOf('"', valStart);
            return valEnd >= valStart ? meta.substring(valStart, valEnd) : null;
        });
    }

    private static final UUID USER_ID = UUID.randomUUID();

    private static Notification mockNotification(NotificationType type, String metaData) {
        Notification n = org.mockito.Mockito.mock(Notification.class);
        when(n.getId()).thenReturn(UUID.randomUUID());
        when(n.getType()).thenReturn(type);
        when(n.getActorId()).thenReturn(UUID.randomUUID());
        when(n.getTargetType()).thenReturn(
                type == NotificationType.CHAT_MESSAGE ? TargetType.CHAT_MESSAGE : TargetType.LIKE);
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
    
    // ──  읽음 처리 성공 (멱등 + Kafka 미발행) ────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("readStates")
    @DisplayName("읽지 않은/이미 읽은 알림 모두 read() 결과 isRead=true (멱등)")
    void read_멱등_isRead_true(String label, Notification notification) {
        // given
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndReceiverId(notificationId, USER_ID))
                .thenReturn(Optional.of(notification));

        // when
        ReadNotificationResult result = notificationService.read(USER_ID, notificationId);

        // then
        assertThat(result.isRead()).isTrue();
        // Service에 KafkaProducer 의존이 없으므로 Kafka 미발행은 구조적으로 보장된다.
    }

    // ── readByRoom ────────────────────────────────────────────────────────────
    // readByRoom은 getId()·getMetaData()만 사용하므로 전용 최소 mock을 사용한다.

    private static Notification chatMock(String metaData) {
        Notification n = mock(Notification.class);
        lenient().when(n.getId()).thenReturn(UUID.randomUUID()); // filter 탈락 시 getId() 미호출 가능
        when(n.getMetaData()).thenReturn(metaData);
        return n;
    }

    @Test
    @DisplayName("해당 방 미읽음 채팅 id만 markAsReadByIds에 전달되고 updatedCount를 반환한다")
    void readByRoom_해당방_미읽음id만_전달되고_updatedCount반환() {
        // given
        String roomId = "room_3f6e";
        String meta = "{\"roomId\":\"room_3f6e\"}";
        Notification n1 = chatMock(meta);
        Notification n2 = chatMock(meta);
        when(notificationRepository.findUnreadChatByReceiverId(USER_ID)).thenReturn(List.of(n1, n2));
        when(notificationRepository.markAsReadByIds(any())).thenReturn(2);

        // when
        ReadByRoomResult result = notificationService.readByRoom(USER_ID, roomId);

        // then
        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.updatedCount()).isEqualTo(2);
        verify(notificationRepository).markAsReadByIds(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(n1.getId(), n2.getId());
    }

    @Test
    @DisplayName("미읽음 채팅 알림이 없으면 markAsReadByIds를 호출하지 않고 updatedCount=0을 반환한다")
    void readByRoom_미읽음없음_markAsReadByIds미호출_updatedCount0() {
        // given
        when(notificationRepository.findUnreadChatByReceiverId(USER_ID)).thenReturn(List.of());

        // when
        ReadByRoomResult result = notificationService.readByRoom(USER_ID, "room_3f6e");

        // then
        assertThat(result.updatedCount()).isEqualTo(0);
        assertThat(result.roomId()).isEqualTo("room_3f6e");
        verify(notificationRepository, never()).markAsReadByIds(any());
    }

    @Test
    @DisplayName("다른 방 알림과 meta_data 파싱 실패 알림은 id 수집에서 제외된다")
    void readByRoom_다른방_파싱실패_제외() {
        // given
        String roomId = "room_3f6e";
        Notification target1 = chatMock("{\"roomId\":\"room_3f6e\"}");
        Notification target2 = chatMock("{\"roomId\":\"room_3f6e\"}");
        Notification otherRoom = chatMock("{\"roomId\":\"room_other\"}");
        Notification badMeta = chatMock("not-json");
        when(notificationRepository.findUnreadChatByReceiverId(USER_ID))
                .thenReturn(List.of(target1, target2, otherRoom, badMeta));
        when(notificationRepository.markAsReadByIds(any())).thenReturn(2);

        // when
        ReadByRoomResult result = notificationService.readByRoom(USER_ID, roomId);

        // then
        assertThat(result.updatedCount()).isEqualTo(2);
        verify(notificationRepository).markAsReadByIds(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(target1.getId(), target2.getId());
    }

    @Test
    @DisplayName("readByRoom은 Kafka 이벤트를 발행하지 않는다")
    void readByRoom_Kafka미발행_구조적보장() {
        // Service에 KafkaProducer/KafkaTemplate 의존이 없으므로 구조적으로 보장된다.
        when(notificationRepository.findUnreadChatByReceiverId(USER_ID)).thenReturn(List.of());

        ReadByRoomResult result = notificationService.readByRoom(USER_ID, "room_3f6e");

        assertThat(result).isNotNull();
        verify(notificationRepository).findUnreadChatByReceiverId(USER_ID);
        verify(notificationRepository, never()).markAsReadByIds(any());
    }

    static Stream<Arguments> readStates() {
        Notification unread = Notification.create(
                USER_ID, UUID.randomUUID(), NotificationType.LIKE, TargetType.LIKE,
                "target-1", "message", null);

        Notification alreadyRead = Notification.create(
                USER_ID, UUID.randomUUID(), NotificationType.LIKE, TargetType.LIKE,
                "target-2", "message", null);
        alreadyRead.read();

        return Stream.of(
                Arguments.of("읽지 않은 알림", unread),
                Arguments.of("이미 읽은 알림", alreadyRead)
        );
    }

}

