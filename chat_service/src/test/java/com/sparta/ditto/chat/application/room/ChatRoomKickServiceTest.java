package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomKickResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomEventPublisher;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatCannotKickSelfException;
import com.sparta.ditto.chat.domain.exception.ChatKickForbiddenException;
import com.sparta.ditto.chat.domain.exception.ChatNotGroupRoomException;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("ChatRoomKickService 테스트")
class ChatRoomKickServiceTest {

    private static final UUID OWNER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");

    private ChatMessageSendService chatMessageSendService;
    private ChatRoomEventPublisher chatRoomEventPublisher;
    private ChatRoomPort chatRoomPort;
    private ChatRoomParticipantPort chatRoomParticipantPort;
    private ChatRoomKickService chatRoomKickService;

    @BeforeEach
    void setUp() {
        chatMessageSendService = mock(ChatMessageSendService.class);
        chatRoomEventPublisher = mock(ChatRoomEventPublisher.class);
        chatRoomPort = mock(ChatRoomPort.class);
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        chatRoomKickService = new ChatRoomKickService(
                chatMessageSendService,
                chatRoomEventPublisher,
                chatRoomPort,
                chatRoomParticipantPort
        );
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("OWNER가 활성 참여자를 강퇴하면 퇴장 처리하고 SYSTEM_KICK 메시지를 저장한다")
    void kick_should_remove_target_and_save_system_message() {
        // given
        givenActiveGroupRoom();
        givenOwnerRequester();
        ChatRoomParticipant target =
                ChatRoomParticipant.join(ROOM_ID, TARGET_ID, ParticipantRole.MEMBER);
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(target));
        given(chatMessageSendService.saveSystemMessage(
                eq(ROOM_ID), eq(TARGET_ID), eq(MessageType.SYSTEM_KICK),
                org.mockito.ArgumentMatchers.anyString()))
                .willReturn(new SentMessage(
                        "msg-1", ROOM_ID, TARGET_ID, TARGET_ID, null,
                        MessageType.SYSTEM_KICK, "사용자가 강퇴되었습니다.", Instant.now(), null));

        // when
        ChatRoomKickResult result = chatRoomKickService.kick(OWNER_ID, ROOM_ID, TARGET_ID);

        // then
        assertThat(result.kickedUserId()).isEqualTo(TARGET_ID);
        assertThat(result.lastVisibleMessageId()).isEqualTo("msg-1");
        assertThat(target.getLeftAt()).isNotNull();
        triggerAfterCommit();
        verify(chatRoomEventPublisher).notifyLeft(TARGET_ID, ROOM_ID);
    }

    @Test
    @DisplayName("자기 자신은 강퇴할 수 없다")
    void kick_should_reject_self() {
        assertThatThrownBy(() -> chatRoomKickService.kick(OWNER_ID, ROOM_ID, OWNER_ID))
                .isInstanceOf(ChatCannotKickSelfException.class);
    }

    @Test
    @DisplayName("OWNER가 아니면 강퇴할 수 없다")
    void kick_should_reject_non_owner() {
        // given
        givenActiveGroupRoom();
        ChatRoomParticipant member =
                ChatRoomParticipant.join(ROOM_ID, OWNER_ID, ParticipantRole.MEMBER);
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> chatRoomKickService.kick(OWNER_ID, ROOM_ID, TARGET_ID))
                .isInstanceOf(ChatKickForbiddenException.class);
        verify(chatMessageSendService, never())
                .saveSystemMessage(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("대상이 활성 참여자가 아니면 강퇴할 수 없다")
    void kick_should_reject_non_participant_target() {
        // given
        givenActiveGroupRoom();
        givenOwnerRequester();
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, TARGET_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomKickService.kick(OWNER_ID, ROOM_ID, TARGET_ID))
                .isInstanceOf(ChatNotParticipantException.class);
    }

    @Test
    @DisplayName("그룹방이 아니면 강퇴할 수 없다")
    void kick_should_reject_non_group_room() {
        // given
        ChatRoom directRoom = mock(ChatRoom.class);
        given(directRoom.getRoomType()).willReturn(RoomType.DIRECT);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(directRoom));

        // when & then
        assertThatThrownBy(() -> chatRoomKickService.kick(OWNER_ID, ROOM_ID, TARGET_ID))
                .isInstanceOf(ChatNotGroupRoomException.class);
    }

    private void givenActiveGroupRoom() {
        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(ROOM_ID);
        given(chatRoom.getRoomType()).willReturn(RoomType.GROUP);
        given(chatRoom.getStatus()).willReturn(RoomStatus.ACTIVE);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
    }

    private void givenOwnerRequester() {
        ChatRoomParticipant owner =
                ChatRoomParticipant.join(ROOM_ID, OWNER_ID, ParticipantRole.OWNER);
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(owner));
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }
}
