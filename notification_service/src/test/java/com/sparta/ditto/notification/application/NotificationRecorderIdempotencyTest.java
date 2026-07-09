package com.sparta.ditto.notification.application;

import com.sparta.ditto.common.entity.BaseEntity;
import com.sparta.ditto.notification.application.dto.ChatNotificationCommand;
import com.sparta.ditto.notification.application.dto.PostNotificationCommand;
import com.sparta.ditto.notification.application.event.NotificationCreatedEvent;
import com.sparta.ditto.notification.application.port.MetaDataPort;
import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import java.lang.reflect.Field;
import org.springframework.util.ReflectionUtils;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TRD 10장 "멱등 성공 skip" — 저장 전 UNIQUE(type, target_id, receiver_id) 사전 exists 체크로
 * 이미 처리된 이벤트를 skip한다. skip된 알림은 저장하지 않고 전송 이벤트도 발행하지 않는다.
 * (사전 체크 방식만 검증한다. DataIntegrityViolationException catch-continue는 금지 사항.)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRecorder - 멱등 성공 skip (사전 exists 체크)")
class NotificationRecorderIdempotencyTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final String ACTOR_NICKNAME = "새벽러너";
    private static final String POST_ID = "post_abc123";
    private static final String LIKE_ID = "like_def789";
    private static final String POST_META = "{\"postId\":\"" + POST_ID + "\"}";

    private static final String MESSAGE_ID = "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
    private static final UUID SENDER_ID = UUID.randomUUID();
    private static final String SENDER_NICKNAME = "짱구";
    private static final String ROOM_ID = "3f6e9a62-6c3f-4ad2-9b2d-47a0e3d4b123";
    private static final UUID RECEIVER_A = UUID.randomUUID();
    private static final UUID RECEIVER_B = UUID.randomUUID();
    private static final String CHAT_PREVIEW = "오늘 같이 공부하실래요?";
    private static final String CHAT_META =
            "{\"roomId\":\"" + ROOM_ID + "\",\"senderNickname\":\"" + SENDER_NICKNAME
                    + "\",\"senderProfileImageUrl\":null}";

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MetaDataPort metaDataPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationRecorder recorder;

    @BeforeEach
    void setUpStubs() {
        lenient().when(metaDataPort.buildPostMetaData(POST_ID)).thenReturn(POST_META);
        lenient().when(metaDataPort.buildChatMetaData(ROOM_ID, SENDER_NICKNAME, null))
                 .thenReturn(CHAT_META);

        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            setField(n, Notification.class, "id", UUID.randomUUID());
            setField(n, BaseEntity.class, "createdAt", Instant.now());
            return n;
        });
    }

    // ── (1) 동일 커맨드 2회 처리 → 저장 1건 · 이벤트 발행 1회 ─────────────────────

    @Test
    @DisplayName("동일 POST_LIKED 커맨드를 2회 처리하면, 사전 exists 체크로 두 번째는 skip되어 "
            + "저장 1건 · 전송 이벤트 발행 1회만 발생한다")
    void recordPost_duplicateCommand_savesOnceAndPublishesOnce() {
        // Given: 첫 호출은 미존재(false), 두 번째 호출은 이미 존재(true)
        when(notificationRepository.existsByTypeAndTargetIdAndReceiverId(
                NotificationType.LIKE, LIKE_ID, OWNER_ID))
                .thenReturn(false, true);

        PostNotificationCommand cmd = PostNotificationCommand.of(
                "POST_LIKED", LIKE_ID, POST_ID, ACTOR_ID, ACTOR_NICKNAME, OWNER_ID);

        // When: 동일 이벤트 2회
        recorder.recordPost(cmd);
        recorder.recordPost(cmd);

        // Then: 저장 1건, 이벤트 1회
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(eventPublisher, times(1)).publishEvent(any(NotificationCreatedEvent.class));
    }

    // ── (2) chat [A,B] 중 A만 기존재 → B만 신규 저장 · B에게만 이벤트 발행 ─────────

    @Test
    @DisplayName("chat receiverIds=[A,B] 중 A만 이미 존재하면, B만 신규 저장하고 B에게만 전송 이벤트를 발행한다 "
            + "(단일 트랜잭션 루프, A는 skip)")
    void recordChat_partialDuplicate_savesOnlyNewReceiverAndPublishesForNewOnly() {
        // Given: A는 기존재(true), B는 미존재(false)
        when(notificationRepository.existsByTypeAndTargetIdAndReceiverId(
                NotificationType.CHAT_MESSAGE, MESSAGE_ID, RECEIVER_A))
                .thenReturn(true);
        when(notificationRepository.existsByTypeAndTargetIdAndReceiverId(
                NotificationType.CHAT_MESSAGE, MESSAGE_ID, RECEIVER_B))
                .thenReturn(false);

        ChatNotificationCommand cmd = ChatNotificationCommand.of(
                MESSAGE_ID, SENDER_ID, SENDER_NICKNAME, null, ROOM_ID,
                List.of(RECEIVER_A, RECEIVER_B), CHAT_PREVIEW);

        // When
        recorder.recordChat(cmd);

        // Then: 저장은 B 1건만
        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getReceiverId()).isEqualTo(RECEIVER_B);

        // 전송 이벤트도 B에게만
        ArgumentCaptor<NotificationCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().receiverId()).isEqualTo(RECEIVER_B);
    }

    // ── (3) 신규 케이스(중복 아님)는 기존과 동일하게 전원 저장 · 전원 발행 ──────────

    @Test
    @DisplayName("중복이 아닌 신규 chat 이벤트(receiverIds=[A,B], 둘 다 미존재)는 "
            + "기존과 동일하게 2건 모두 저장하고 2건 모두 전송 이벤트를 발행한다")
    void recordChat_allNew_savesAllAndPublishesAll() {
        // Given: exists 기본값 false (둘 다 미존재)

        ChatNotificationCommand cmd = ChatNotificationCommand.of(
                MESSAGE_ID, SENDER_ID, SENDER_NICKNAME, null, ROOM_ID,
                List.of(RECEIVER_A, RECEIVER_B), CHAT_PREVIEW);

        // When
        recorder.recordChat(cmd);

        // Then: 2건 모두 저장 · 2건 모두 발행 (skip 없음)
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(eventPublisher, times(2)).publishEvent(any(NotificationCreatedEvent.class));
    }

    private static void setField(Object target, Class<?> clazz, String name, Object value) {
        Field field = ReflectionUtils.findField(clazz, name);
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, target, value);
    }
}