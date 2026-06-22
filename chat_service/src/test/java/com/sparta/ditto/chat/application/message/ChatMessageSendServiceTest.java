package com.sparta.ditto.chat.application.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.message.dto.ChatMessageSendCommand;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.port.ChatMessagePublisher;
import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.ChatRoomMetadataService;
import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.domain.message.MessageIdGenerator;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageDocument;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageMongoRepository;
import com.sparta.ditto.common.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatMessageSendService 테스트")
class ChatMessageSendServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_MESSAGE_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private ChatMessageMongoRepository chatMessageMongoRepository;
    private ChatParticipantValidator chatParticipantValidator;
    private MessageIdGenerator messageIdGenerator;
    private ChatRoomMetadataService chatRoomMetadataService;
    private ChatMessagePublisher chatMessagePublisher;
    private ChatMessageSendService chatMessageSendService;

    @BeforeEach
    void setUp() {
        chatMessageMongoRepository = mock(ChatMessageMongoRepository.class);
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        messageIdGenerator = mock(MessageIdGenerator.class);
        chatRoomMetadataService = mock(ChatRoomMetadataService.class);
        chatMessagePublisher = mock(ChatMessagePublisher.class);
        chatMessageSendService = new ChatMessageSendService(
                chatMessageMongoRepository, chatParticipantValidator,
                messageIdGenerator, chatRoomMetadataService, chatMessagePublisher);
    }

    @Test
    @DisplayName("성공 - 저장 후 lastMessage 갱신, ACK, 브로드캐스트가 호출된다")
    void success_send() {
        // given
        given(messageIdGenerator.generate()).willReturn("msg-1");
        given(chatMessageMongoRepository.save(any()))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        chatMessageSendService.sendUserMessage(command());

        // then
        verify(chatMessageMongoRepository).save(any(ChatMessageDocument.class));
        verify(chatRoomMetadataService).updateLastMessage(eq(ROOM_ID), eq("msg-1"), any());
        verify(chatMessagePublisher).ackToSender(eq(SENDER_ID), any(SentMessage.class));
        verify(chatMessagePublisher).broadcast(eq(ROOM_ID), any(SentMessage.class));
    }

    @Test
    @DisplayName("실패 - 참여자가 아니면 저장 없이 예외가 발생한다")
    void fail_not_participant() {
        // given
        willThrow(new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .given(chatParticipantValidator).ensureActiveParticipant(any(), any());

        // when & then
        assertThatThrownBy(() -> chatMessageSendService.sendUserMessage(command()))
                .isInstanceOf(BusinessException.class);
        verify(chatMessageMongoRepository, never()).save(any());
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
        verify(chatMessageMongoRepository, never()).save(any());
    }

    private ChatMessageSendCommand command() {
        return new ChatMessageSendCommand(
                ROOM_ID, SENDER_ID, CLIENT_MESSAGE_ID, MessageType.TEXT, "hello");
    }
}
