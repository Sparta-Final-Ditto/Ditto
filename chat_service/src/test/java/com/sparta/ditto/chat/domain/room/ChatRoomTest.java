package com.sparta.ditto.chat.domain.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatRoom 도메인 테스트")
class ChatRoomTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String MESSAGE_ID = "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";

    @Test
    @DisplayName("성공 - 1:1 채팅방을 생성한다")
    void createDirect_success() {
        // given
        UUID createdBy = USER_ID;

        // when
        ChatRoom chatRoom = ChatRoom.createDirect(createdBy);

        // then
        assertThat(chatRoom.getRoomType()).isEqualTo(RoomType.DIRECT);
        assertThat(chatRoom.getRoomName()).isNull();
        assertThat(chatRoom.getStatus()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(chatRoom.getCreatedBy()).isEqualTo(createdBy);
    }

    @Test
    @DisplayName("성공 - 그룹 채팅방을 생성한다")
    void createGroup_success() {
        // given
        UUID createdBy = USER_ID;

        // when
        ChatRoom chatRoom = ChatRoom.createGroup("스터디방", createdBy);

        // then
        assertThat(chatRoom.getRoomType()).isEqualTo(RoomType.GROUP);
        assertThat(chatRoom.getRoomName()).isEqualTo("스터디방");
        assertThat(chatRoom.getStatus()).isEqualTo(RoomStatus.ACTIVE);
    }

    @Test
    @DisplayName("실패 - 그룹 채팅방 이름은 비어 있을 수 없다")
    void createGroup_fail_blank_room_name() {
        // given
        UUID createdBy = USER_ID;

        // when & then
        assertThatThrownBy(() -> ChatRoom.createGroup(" ", createdBy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("성공 - 마지막 메시지 정보를 갱신한다")
    void updateLastMessage_success() {
        // given
        ChatRoom chatRoom = ChatRoom.createDirect(USER_ID);
        Instant messageCreatedAt = Instant.parse("2026-06-18T00:00:00Z");

        // when
        chatRoom.updateLastMessage(MESSAGE_ID, messageCreatedAt);

        // then
        assertThat(chatRoom.getLastMessageId()).isEqualTo(MESSAGE_ID);
        assertThat(chatRoom.getLastMessageAt()).isEqualTo(messageCreatedAt);
    }

    @Test
    @DisplayName("성공 - 채팅방을 비활성화한 뒤 재활성화한다")
    void inactivate_and_reactivate_success() {
        // given
        UUID userId = USER_ID;
        ChatRoom chatRoom = ChatRoom.createDirect(userId);

        // when
        chatRoom.inactivate(userId);

        // then
        assertThat(chatRoom.getStatus()).isEqualTo(RoomStatus.INACTIVE);
        assertThat(chatRoom.getInactivatedBy()).isEqualTo(userId);
        assertThat(chatRoom.getInactivatedAt()).isNotNull();

        // when
        chatRoom.reactivate();

        // then
        assertThat(chatRoom.getStatus()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(chatRoom.getInactivatedBy()).isNull();
        assertThat(chatRoom.getInactivatedAt()).isNull();
    }

    @Test
    @DisplayName("성공 - 저장 전 생성/수정 시각을 초기화한다")
    void prePersist_success() {
        // given
        ChatRoom chatRoom = ChatRoom.createDirect(USER_ID);

        // when
        chatRoom.prePersist();

        // then
        assertThat(chatRoom.getCreatedAt()).isNotNull();
        assertThat(chatRoom.getUpdatedAt()).isNotNull();
    }
}
