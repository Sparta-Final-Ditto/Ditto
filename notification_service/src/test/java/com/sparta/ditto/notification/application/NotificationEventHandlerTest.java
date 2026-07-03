package com.sparta.ditto.notification.application;

import com.sparta.ditto.notification.application.dto.ChatNotificationCommand;
import com.sparta.ditto.notification.application.dto.PostNotificationCommand;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventHandler - 검증/skip 후 Recorder 위임 (트랜잭션 경계 분리)")
class NotificationEventHandlerTest {

    // post-events 공통 값
    private static final UUID OWNER_ID = UUID.randomUUID();   // 게시글 작성자 = 수신자
    private static final UUID ACTOR_ID = UUID.randomUUID();   // 좋아요/댓글 행위자
    private static final String ACTOR_NICKNAME = "새벽러너";
    private static final String POST_ID = "post_abc123";
    private static final String LIKE_ID = "like_def789";
    private static final String COMMENT_ID = "comment_def456";

    // chat-message-created 공통 값
    private static final String MESSAGE_ID = "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
    private static final UUID SENDER_ID = UUID.randomUUID();
    private static final String SENDER_NICKNAME = "짱구";
    private static final String ROOM_ID = "3f6e9a62-6c3f-4ad2-9b2d-47a0e3d4b123";
    private static final UUID RECEIVER_1 = UUID.randomUUID();

    @Mock
    private NotificationRecorder notificationRecorder;

    @InjectMocks
    private NotificationEventHandler handler;

    // ── 알림 미생성 정상 종료 (no-op 통합): 저장 트랜잭션(Recorder) 미호출로 관찰 ─────

    static Stream<Arguments> noOpScenarios() {
        UUID selfId = UUID.randomUUID();
        return Stream.of(
                // ① 비구독 eventType (POST_CREATED 등) → Recorder 미호출
                Arguments.of(
                        "비구독 eventType(POST_CREATED)",
                        (Consumer<NotificationEventHandler>) h -> h.handlePostEvent(
                                PostNotificationCommand.of(
                                        "POST_CREATED", null, POST_ID, ACTOR_ID, ACTOR_NICKNAME, OWNER_ID))),
                Arguments.of(
                        "비구독 eventType(POST_DELETED)",
                        (Consumer<NotificationEventHandler>) h -> h.handlePostEvent(
                                PostNotificationCommand.of(
                                        "POST_DELETED", null, POST_ID, ACTOR_ID, ACTOR_NICKNAME, OWNER_ID))),
                // ② 자가 행위(actorId == receiverId) — 좋아요
                Arguments.of(
                        "자가 좋아요(actorId == ownerId)",
                        (Consumer<NotificationEventHandler>) h -> h.handlePostEvent(
                                PostNotificationCommand.of(
                                        "POST_LIKED", LIKE_ID, POST_ID, selfId, ACTOR_NICKNAME, selfId))),
                // ② 자가 행위 — 댓글
                Arguments.of(
                        "자가 댓글(actorId == ownerId)",
                        (Consumer<NotificationEventHandler>) h -> h.handlePostEvent(
                                PostNotificationCommand.of(
                                        "POST_COMMENTED", COMMENT_ID, POST_ID, selfId, ACTOR_NICKNAME, selfId))),
                // ③ receiverIds 빈 배열 → no-op
                Arguments.of(
                        "채팅 receiverIds 빈 배열",
                        (Consumer<NotificationEventHandler>) h -> h.handleChatMessage(
                                ChatNotificationCommand.of(
                                        MESSAGE_ID, SENDER_ID, SENDER_NICKNAME, null, ROOM_ID,
                                        List.of(), "안녕하세요")))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noOpScenarios")
    @DisplayName("비구독 이벤트/자가 행위/빈 receiverIds는 예외 없이 저장 트랜잭션(Recorder)을 호출하지 않는다")
    void noOp_doesNotInvokeRecorder(String description, Consumer<NotificationEventHandler> invocation) {
        // When / Then
        assertThatCode(() -> invocation.accept(handler)).doesNotThrowAnyException();
        verifyNoInteractions(notificationRecorder);
    }

    // ── 계약 위반 → 예외 (Consumer 재시도 유도) + Recorder 미호출 ──────────────────

    static Stream<Arguments> contractViolationScenarios() {
        return Stream.of(
                // ① chat preview null/blank
                Arguments.of(
                        "chat preview=null",
                        (Consumer<NotificationEventHandler>) h -> h.handleChatMessage(
                                ChatNotificationCommand.of(
                                        MESSAGE_ID, SENDER_ID, SENDER_NICKNAME, null, ROOM_ID,
                                        List.of(RECEIVER_1), null))),
                Arguments.of(
                        "chat preview=\"\"",
                        (Consumer<NotificationEventHandler>) h -> h.handleChatMessage(
                                ChatNotificationCommand.of(
                                        MESSAGE_ID, SENDER_ID, SENDER_NICKNAME, null, ROOM_ID,
                                        List.of(RECEIVER_1), ""))),
                Arguments.of(
                        "chat preview=\"   \"(blank)",
                        (Consumer<NotificationEventHandler>) h -> h.handleChatMessage(
                                ChatNotificationCommand.of(
                                        MESSAGE_ID, SENDER_ID, SENDER_NICKNAME, null, ROOM_ID,
                                        List.of(RECEIVER_1), "   "))),
                // ② receiverIds null (빈 배열과 구분 — 빈 배열은 no-op, null은 계약 위반)
                Arguments.of(
                        "chat receiverIds=null",
                        (Consumer<NotificationEventHandler>) h -> h.handleChatMessage(
                                ChatNotificationCommand.of(
                                        MESSAGE_ID, SENDER_ID, SENDER_NICKNAME, null, ROOM_ID,
                                        null, "안녕하세요"))),
                // ③ POST_LIKED/POST_COMMENTED 필수 필드 null (actorId != ownerId 로 자가행위 아님 보장)
                Arguments.of(
                        "POST_LIKED targetId(likeId)=null",
                        (Consumer<NotificationEventHandler>) h -> h.handlePostEvent(
                                PostNotificationCommand.of(
                                        "POST_LIKED", null, POST_ID, ACTOR_ID, ACTOR_NICKNAME, OWNER_ID))),
                Arguments.of(
                        "POST_LIKED postId=null",
                        (Consumer<NotificationEventHandler>) h -> h.handlePostEvent(
                                PostNotificationCommand.of(
                                        "POST_LIKED", LIKE_ID, null, ACTOR_ID, ACTOR_NICKNAME, OWNER_ID))),
                Arguments.of(
                        "POST_LIKED ownerId=null",
                        (Consumer<NotificationEventHandler>) h -> h.handlePostEvent(
                                PostNotificationCommand.of(
                                        "POST_LIKED", LIKE_ID, POST_ID, ACTOR_ID, ACTOR_NICKNAME, null))),
                Arguments.of(
                        "POST_LIKED actorNickname=null",
                        (Consumer<NotificationEventHandler>) h -> h.handlePostEvent(
                                PostNotificationCommand.of(
                                        "POST_LIKED", LIKE_ID, POST_ID, ACTOR_ID, null, OWNER_ID))),
                Arguments.of(
                        "POST_COMMENTED actorId=null",
                        (Consumer<NotificationEventHandler>) h -> h.handlePostEvent(
                                PostNotificationCommand.of(
                                        "POST_COMMENTED", COMMENT_ID, POST_ID, null, ACTOR_NICKNAME, OWNER_ID))),
                // ④ chat 필수 필드 null
                Arguments.of(
                        "chat messageId=null",
                        (Consumer<NotificationEventHandler>) h -> h.handleChatMessage(
                                ChatNotificationCommand.of(
                                        null, SENDER_ID, SENDER_NICKNAME, null, ROOM_ID,
                                        List.of(RECEIVER_1), "안녕하세요"))),
                Arguments.of(
                        "chat roomId=null",
                        (Consumer<NotificationEventHandler>) h -> h.handleChatMessage(
                                ChatNotificationCommand.of(
                                        MESSAGE_ID, SENDER_ID, SENDER_NICKNAME, null, null,
                                        List.of(RECEIVER_1), "안녕하세요"))),
                Arguments.of(
                        "chat senderNickname=null",
                        (Consumer<NotificationEventHandler>) h -> h.handleChatMessage(
                                ChatNotificationCommand.of(
                                        MESSAGE_ID, SENDER_ID, null, null, ROOM_ID,
                                        List.of(RECEIVER_1), "안녕하세요")))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractViolationScenarios")
    @DisplayName("계약 위반(preview null/blank, receiverIds null, 필수 필드 null)은 "
            + "예외를 던져 Consumer 재시도 정책을 태우고 저장 트랜잭션(Recorder)을 호출하지 않는다")
    void contractViolation_throwsAndDoesNotInvokeRecorder(
            String description, Consumer<NotificationEventHandler> invocation) {
        // When / Then
        assertThatThrownBy(() -> invocation.accept(handler))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notificationRecorder);
    }
}