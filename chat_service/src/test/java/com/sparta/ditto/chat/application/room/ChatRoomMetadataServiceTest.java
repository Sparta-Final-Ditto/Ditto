package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatRoomMetadataService 테스트")
class ChatRoomMetadataServiceTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final String MESSAGE_ID = "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
    private static final Instant MESSAGE_CREATED_AT =
            Instant.parse("2026-06-20T01:00:00Z");

    private ChatRoomRepository chatRoomRepository;
    private ChatRoomMetadataService chatRoomMetadataService;

    @BeforeEach
    void setUp() {
        chatRoomRepository = mock(ChatRoomRepository.class);
        chatRoomMetadataService = new ChatRoomMetadataService(chatRoomRepository);
    }

    @Test
    @DisplayName("마지막 메시지 메타데이터를 갱신한다")
    void updateLastMessage_success() {
        // given
        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));

        // when
        chatRoomMetadataService.updateLastMessage(
                ROOM_ID,
                MESSAGE_ID,
                MESSAGE_CREATED_AT
        );

        // then
        verify(chatRoom).updateLastMessage(MESSAGE_ID, MESSAGE_CREATED_AT);
    }

    @Test
    @DisplayName("채팅방이 없으면 마지막 메시지를 갱신할 수 없다")
    void updateLastMessage_fail_room_not_found() {
        // given
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomMetadataService.updateLastMessage(
                ROOM_ID,
                MESSAGE_ID,
                MESSAGE_CREATED_AT
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("messageId가 비어 있으면 마지막 메시지를 갱신할 수 없다")
    void updateLastMessage_fail_blank_message_id() {
        // when & then
        assertThatThrownBy(() -> chatRoomMetadataService.updateLastMessage(
                ROOM_ID,
                " ",
                MESSAGE_CREATED_AT
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("필수 입력값이 없으면 마지막 메시지를 갱신할 수 없다")
    void updateLastMessage_fail_null_room_id() {
        // when & then
        assertThatThrownBy(() -> chatRoomMetadataService.updateLastMessage(
                null,
                MESSAGE_ID,
                MESSAGE_CREATED_AT
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("메시지 생성 시각이 없으면 마지막 메시지를 갱신할 수 없다")
    void updateLastMessage_fail_null_message_created_at() {
        // when & then
        assertThatThrownBy(() -> chatRoomMetadataService.updateLastMessage(
                ROOM_ID,
                MESSAGE_ID,
                null
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }
}
