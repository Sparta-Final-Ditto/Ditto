package com.sparta.ditto.chat.application.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.event.ChatSystemMessageBroadcastRequestedEvent;
import com.sparta.ditto.chat.application.message.dto.ChatMessageSendCommand;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.port.ChatMessageCommandPort;
import com.sparta.ditto.chat.application.message.port.ChatMessageDedupStore;
import com.sparta.ditto.chat.application.message.port.ChatMessagePublisher;
import com.sparta.ditto.chat.application.message.port.ChatMessageQueryPort;
import com.sparta.ditto.chat.application.message.port.DedupBeginResult;
import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.ChatRoomMetadataService;
import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.domain.message.MessageIdGenerator;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.common.exception.BusinessException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("ChatMessageSendService 테스트")
class ChatMessageSendServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_MESSAGE_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private ChatMessageCommandPort chatMessageCommandPort;
    private ChatMessageQueryPort chatMessageQueryPort;
    private ChatParticipantValidator chatParticipantValidator;
    private MessageIdGenerator messageIdGenerator;
    private ChatRoomMetadataService chatRoomMetadataService;
    private ChatMessagePublisher chatMessagePublisher;
    private ChatMessageDedupStore chatMessageDedupStore;
    private ChatMessageCommitService chatMessageCommitService;
    private ApplicationEventPublisher applicationEventPublisher;
    private ChatMessageSendService chatMessageSendService;

    @BeforeEach
    void setUp() {
        chatMessageCommandPort = mock(ChatMessageCommandPort.class);
        chatMessageQueryPort = mock(ChatMessageQueryPort.class);
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        messageIdGenerator = mock(MessageIdGenerator.class);
        chatRoomMetadataService = mock(ChatRoomMetadataService.class);
        chatMessagePublisher = mock(ChatMessagePublisher.class);
        chatMessageDedupStore = mock(ChatMessageDedupStore.class);
        chatMessageCommitService = mock(ChatMessageCommitService.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        chatMessageSendService = new ChatMessageSendService(
                chatMessageCommandPort, chatMessageQueryPort, chatParticipantValidator,
                messageIdGenerator, chatRoomMetadataService,
                chatMessagePublisher, chatMessageDedupStore,
                chatMessageCommitService, applicationEventPublisher);
    }

    @Test
    @DisplayName("성공 - 신규 메시지: 저장/lastMessage/complete/ACK/브로드캐스트 호출")
    void success_new_message() {
        // given
        given(chatMessageDedupStore.begin(any(), any(), any()))
                .willReturn(DedupBeginResult.newRequest());
        given(messageIdGenerator.generate()).willReturn("msg-1");
        given(chatMessageCommandPort.saveUserMessage(any(), any(), any(), any(), any(), any()))
                .willReturn(existingMessage("msg-1"));

        // when
        chatMessageSendService.sendUserMessage(command());

        // then
        verify(chatMessageCommandPort)
                .saveUserMessage(eq("msg-1"), eq(ROOM_ID), eq(SENDER_ID),
                        eq(CLIENT_MESSAGE_ID), eq(MessageType.TEXT), eq("hello"));
        // 메타갱신 + 알림 이벤트 발행은 ChatMessageCommitService가 트랜잭션으로 묶어 처리한다.
        verify(chatMessageCommitService)
                .commitMetadataAndRegisterNotification(eq(ROOM_ID), any(SentMessage.class));
        verify(chatMessageDedupStore)
                .complete(eq(ROOM_ID), eq(SENDER_ID), eq(CLIENT_MESSAGE_ID), eq("msg-1"));
        verify(chatMessagePublisher).ackToSender(eq(SENDER_ID), any(SentMessage.class));
        verify(chatMessagePublisher).broadcast(eq(ROOM_ID), any(SentMessage.class));
    }

    @Test
    @DisplayName("중복(완료) - 저장/브로드캐스트 없이 기존 messageId로 ACK만 반환")
    void duplicate_completed_acks_existing() {
        // given
        given(chatMessageDedupStore.begin(any(), any(), any()))
                .willReturn(DedupBeginResult.duplicateCompleted("msg-1"));
        given(chatMessageQueryPort.findByMessageId("msg-1"))
                .willReturn(Optional.of(existingMessage("msg-1")));

        // when
        chatMessageSendService.sendUserMessage(command());

        // then
        verify(chatMessagePublisher).ackToSender(eq(SENDER_ID), any(SentMessage.class));
        verify(chatMessageCommandPort, never())
                .saveUserMessage(any(), any(), any(), any(), any(), any());
        verify(chatMessagePublisher, never()).broadcast(any(), any());
    }

    @Test
    @DisplayName("중복(처리중) - 새 저장 없이 예외가 발생한다")
    void duplicate_processing_throws() {
        // given
        given(chatMessageDedupStore.begin(any(), any(), any()))
                .willReturn(DedupBeginResult.duplicateProcessing());

        // when & then
        assertThatThrownBy(() -> chatMessageSendService.sendUserMessage(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChatErrorCode.CHAT_DUPLICATE_PROCESSING);
        verify(chatMessageCommandPort, never())
                .saveUserMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("성공 - 시스템 메시지 저장 후 lastMessage를 갱신하고 커밋 후 브로드캐스트 이벤트를 발행한다")
    void success_save_system_message() {
        // given
        given(messageIdGenerator.generate()).willReturn("system-msg-1");
        given(chatMessageCommandPort.saveSystemMessage(any(), any(), any(), any(), any()))
                .willReturn(systemMessage("system-msg-1"));

        // when
        chatMessageSendService.saveSystemMessage(
                ROOM_ID,
                SENDER_ID,
                MessageType.SYSTEM_LEAVE,
                "사용자가 채팅방을 나갔습니다."
        );

        // then
        verify(chatMessageCommandPort)
                .saveSystemMessage(eq("system-msg-1"), eq(ROOM_ID), eq(SENDER_ID),
                        eq(MessageType.SYSTEM_LEAVE), any());
        verify(chatRoomMetadataService)
                .updateLastMessage(eq(ROOM_ID), eq("system-msg-1"), any());
        verify(chatMessagePublisher, never()).ackToSender(any(), any());
        // broadcast는 직접 호출하지 않고 커밋 이후 이벤트로 발행한다.
        verify(chatMessagePublisher, never()).broadcast(any(), any());
        verify(applicationEventPublisher)
                .publishEvent(any(ChatSystemMessageBroadcastRequestedEvent.class));
    }

    @Test
    @DisplayName("실패 - 참여자가 아니면 dedup/저장 없이 예외가 발생한다")
    void fail_not_participant() {
        // given
        willThrow(new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .given(chatParticipantValidator).ensureActiveParticipant(any(), any());

        // when & then
        assertThatThrownBy(() -> chatMessageSendService.sendUserMessage(command()))
                .isInstanceOf(BusinessException.class);
        verify(chatMessageDedupStore, never()).begin(any(), any(), any());
        verify(chatMessageCommandPort, never())
                .saveUserMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("실패 - 비활성 방이면 저장 없이 예외가 발생한다")
    void fail_inactive_room() {
        // given
        willThrow(new BusinessException(ChatErrorCode.CHAT_ROOM_INACTIVE))
                .given(chatParticipantValidator).ensureRoomActive(any());

        // when & then
        assertThatThrownBy(() -> chatMessageSendService.sendUserMessage(command()))
                .isInstanceOf(BusinessException.class);
        verify(chatMessageCommandPort, never())
                .saveUserMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("저장 실패 - dedup 잠금을 해제하고 예외를 전파한다")
    void release_dedup_when_save_fails() {
        // given
        given(chatMessageDedupStore.begin(any(), any(), any()))
                .willReturn(DedupBeginResult.newRequest());
        given(messageIdGenerator.generate()).willReturn("msg-1");
        given(chatMessageCommandPort.saveUserMessage(any(), any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("mongo down"));

        // when & then
        assertThatThrownBy(() -> chatMessageSendService.sendUserMessage(command()))
                .isInstanceOf(RuntimeException.class);
        verify(chatMessageDedupStore)
                .release(eq(ROOM_ID), eq(SENDER_ID), eq(CLIENT_MESSAGE_ID));
        verify(chatMessageDedupStore, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("중복(완료)인데 원본이 없으면 - dedup 정리 후 예외, ACK 안 함")
    void duplicate_completed_but_missing_original() {
        // given
        given(chatMessageDedupStore.begin(any(), any(), any()))
                .willReturn(DedupBeginResult.duplicateCompleted("msg-1"));
        given(chatMessageQueryPort.findByMessageId("msg-1"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatMessageSendService.sendUserMessage(command()))
                .isInstanceOf(BusinessException.class);
        verify(chatMessageDedupStore)
                .release(eq(ROOM_ID), eq(SENDER_ID), eq(CLIENT_MESSAGE_ID));
        verify(chatMessagePublisher, never()).ackToSender(any(), any());
    }

    private ChatMessageSendCommand command() {
        return new ChatMessageSendCommand(
                ROOM_ID, SENDER_ID, CLIENT_MESSAGE_ID, MessageType.TEXT, "hello");
    }

    private SentMessage existingMessage(String messageId) {
        return new SentMessage(
                messageId,
                ROOM_ID,
                SENDER_ID,
                null,
                CLIENT_MESSAGE_ID,
                MessageType.TEXT,
                "hello",
                Instant.now(),
                null
        );
    }

    private SentMessage systemMessage(String messageId) {
        return new SentMessage(
                messageId,
                ROOM_ID,
                null,
                SENDER_ID,
                null,
                MessageType.SYSTEM_LEAVE,
                "사용자가 채팅방을 나갔습니다.",
                Instant.now(),
                null
        );
    }
}
