package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatReadCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatReadResult;
import com.sparta.ditto.chat.domain.exception.ChatMessageNotFoundException;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageDocument;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageMongoRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ChatReadService 테스트")
class ChatReadServiceTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_MESSAGE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final String CURRENT_MESSAGE_ID =
            "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
    private static final String NEXT_MESSAGE_ID =
            "018f7b7a-4d3d-7c22-9f1b-2a3c4d5e6f71";
    private static final String PREVIOUS_MESSAGE_ID =
            "018f7b7a-4d3b-7c22-9f1b-2a3c4d5e6f69";
    private static final Instant CURRENT_MESSAGE_CREATED_AT =
            Instant.parse("2026-06-22T10:00:00Z");
    private static final Instant NEXT_MESSAGE_CREATED_AT =
            Instant.parse("2026-06-22T10:01:00Z");
    private static final Instant PREVIOUS_MESSAGE_CREATED_AT =
            Instant.parse("2026-06-22T09:59:00Z");

    private ChatParticipantValidator chatParticipantValidator;
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    private ChatMessageMongoRepository chatMessageMongoRepository;
    private ChatReadService chatReadService;

    @BeforeEach
    void setUp() {
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        chatRoomParticipantRepository = mock(ChatRoomParticipantRepository.class);
        chatMessageMongoRepository = mock(ChatMessageMongoRepository.class);
        chatReadService = new ChatReadService(
                chatParticipantValidator,
                chatRoomParticipantRepository,
                chatMessageMongoRepository
        );
    }

    @Test
    @DisplayName("처음 읽음 처리하면 마지막 읽은 메시지와 읽은 시각을 갱신한다")
    void updateReadState_success_first_read() {
        // given
        ChatRoomParticipant participant = participant();
        givenParticipant(participant);
        givenMessage(NEXT_MESSAGE_ID, NEXT_MESSAGE_CREATED_AT);

        // when
        ChatReadResult result = chatReadService.updateReadState(command(NEXT_MESSAGE_ID));

        // then
        verify(chatParticipantValidator).ensureRoomActive(ROOM_ID);
        assertThat(participant.getLastReadMessageId()).isEqualTo(NEXT_MESSAGE_ID);
        assertThat(participant.getLastReadAt()).isNotNull();
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.lastReadMessageId()).isEqualTo(NEXT_MESSAGE_ID);
        assertThat(result.lastReadAt()).isEqualTo(participant.getLastReadAt());
    }

    @Test
    @DisplayName("기존 읽음 위치보다 최신 메시지이면 읽음 위치를 갱신한다")
    void updateReadState_success_update_to_newer_message() {
        // given
        ChatRoomParticipant participant = participant();
        participant.updateLastRead(CURRENT_MESSAGE_ID, Instant.now());
        givenParticipant(participant);
        givenMessage(CURRENT_MESSAGE_ID, CURRENT_MESSAGE_CREATED_AT);
        givenMessage(NEXT_MESSAGE_ID, NEXT_MESSAGE_CREATED_AT);

        // when
        ChatReadResult result = chatReadService.updateReadState(command(NEXT_MESSAGE_ID));

        // then
        assertThat(participant.getLastReadMessageId()).isEqualTo(NEXT_MESSAGE_ID);
        assertThat(result.lastReadMessageId()).isEqualTo(NEXT_MESSAGE_ID);
    }

    @Test
    @DisplayName("기존 읽음 위치보다 오래된 메시지이면 읽음 위치를 되돌리지 않는다")
    void updateReadState_ignore_older_message() {
        // given
        ChatRoomParticipant participant = participant();
        participant.updateLastRead(CURRENT_MESSAGE_ID, Instant.now());
        givenParticipant(participant);
        givenMessage(CURRENT_MESSAGE_ID, CURRENT_MESSAGE_CREATED_AT);
        givenMessage(PREVIOUS_MESSAGE_ID, PREVIOUS_MESSAGE_CREATED_AT);

        // when
        ChatReadResult result = chatReadService.updateReadState(command(PREVIOUS_MESSAGE_ID));

        // then
        assertThat(participant.getLastReadMessageId()).isEqualTo(CURRENT_MESSAGE_ID);
        assertThat(result.lastReadMessageId()).isEqualTo(CURRENT_MESSAGE_ID);
    }

    @Test
    @DisplayName("읽음 처리 기준 메시지가 없으면 메시지 없음 예외를 던진다")
    void updateReadState_fail_message_not_found() {
        // given
        ChatRoomParticipant participant = participant();
        givenParticipant(participant);
        given(chatMessageMongoRepository.findByMessageIdAndRoomId(NEXT_MESSAGE_ID, ROOM_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatReadService.updateReadState(command(NEXT_MESSAGE_ID)))
                .isInstanceOf(ChatMessageNotFoundException.class);
    }

    @Test
    @DisplayName("현재 참여자가 아니면 읽음 상태를 갱신할 수 없다")
    void updateReadState_fail_not_participant() {
        // given
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatReadService.updateReadState(command(NEXT_MESSAGE_ID)))
                .isInstanceOf(ChatNotParticipantException.class);
    }

    @Test
    @DisplayName("필수 입력값이 없으면 읽음 상태를 갱신할 수 없다")
    void updateReadState_fail_null_command() {
        // when & then
        assertThatThrownBy(() -> chatReadService.updateReadState(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    private ChatRoomParticipant participant() {
        return ChatRoomParticipant.join(
                ROOM_ID,
                REQUESTER_ID,
                ParticipantRole.MEMBER
        );
    }

    private void givenParticipant(ChatRoomParticipant participant) {
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.of(participant));
    }

    private void givenMessage(String messageId, Instant createdAt) {
        given(chatMessageMongoRepository.findByMessageIdAndRoomId(messageId, ROOM_ID))
                .willReturn(Optional.of(message(messageId, createdAt)));
    }

    private ChatMessageDocument message(String messageId, Instant createdAt) {
        ChatMessageDocument message = ChatMessageDocument.createUserMessage(
                messageId,
                ROOM_ID,
                REQUESTER_ID,
                CLIENT_MESSAGE_ID,
                MessageType.TEXT,
                "테스트 메시지"
        );
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }

    private ChatReadCommand command(String lastReadMessageId) {
        return ChatReadCommand.of(REQUESTER_ID, ROOM_ID, lastReadMessageId);
    }
}
