package com.sparta.ditto.chat.domain.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatRoomParticipant 도메인 테스트")
class ChatRoomParticipantTest {

    private static final String MESSAGE_ID = "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";

    @Test
    @DisplayName("성공 - 채팅방 참여자를 생성한다")
    void join_success() {
        // given
        UUID roomId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        // when
        ChatRoomParticipant participant = ChatRoomParticipant.join(
                roomId,
                userId,
                ParticipantRole.MEMBER
        );

        // then
        assertThat(participant.getRoomId()).isEqualTo(roomId);
        assertThat(participant.getUserId()).isEqualTo(userId);
        assertThat(participant.getRole()).isEqualTo(ParticipantRole.MEMBER);
        assertThat(participant.isHidden()).isFalse();
        assertThat(participant.isNotificationEnabled()).isTrue();
    }

    @Test
    @DisplayName("성공 - 채팅방 나가기와 재입장을 처리한다")
    void leave_and_rejoin_success() {
        // given
        ChatRoomParticipant participant = ChatRoomParticipant.join(
                UUID.fromString("00000000-0000-0000-0000-000000000100"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ParticipantRole.MEMBER
        );

        // when
        participant.leave(MESSAGE_ID);

        // then
        assertThat(participant.getLeftAt()).isNotNull();
        assertThat(participant.getLastVisibleMessageId()).isEqualTo(MESSAGE_ID);

        // when
        participant.hide();
        participant.rejoin();

        // then
        assertThat(participant.getLeftAt()).isNull();
        assertThat(participant.isHidden()).isFalse();
        assertThat(participant.getLastVisibleMessageId()).isEqualTo(MESSAGE_ID);
    }

    @Test
    @DisplayName("성공 - 마지막 읽음 상태를 갱신한다")
    void updateLastRead_success() {
        // given
        ChatRoomParticipant participant = ChatRoomParticipant.join(
                UUID.fromString("00000000-0000-0000-0000-000000000100"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ParticipantRole.MEMBER
        );
        Instant lastReadAt = Instant.parse("2026-06-18T00:00:00Z");

        // when
        participant.updateLastRead(MESSAGE_ID, lastReadAt);

        // then
        assertThat(participant.getLastReadMessageId()).isEqualTo(MESSAGE_ID);
        assertThat(participant.getLastReadAt()).isEqualTo(lastReadAt);
    }

    @Test
    @DisplayName("실패 - 마지막 읽음 메시지 ID는 null일 수 없다")
    void updateLastRead_fail_null_message_id() {
        // given
        ChatRoomParticipant participant = ChatRoomParticipant.join(
                UUID.fromString("00000000-0000-0000-0000-000000000100"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ParticipantRole.MEMBER
        );

        // when & then
        assertThatThrownBy(() -> participant.updateLastRead(null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("성공 - 알림 설정을 변경한다")
    void changeNotificationEnabled_success() {
        // given
        ChatRoomParticipant participant = ChatRoomParticipant.join(
                UUID.fromString("00000000-0000-0000-0000-000000000100"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ParticipantRole.MEMBER
        );

        // when
        participant.changeNotificationEnabled(false);

        // then
        assertThat(participant.isNotificationEnabled()).isFalse();
    }

    @Test
    @DisplayName("성공 - 저장 전 참여 시각을 초기화한다")
    void prePersist_success() {
        // given
        ChatRoomParticipant participant = ChatRoomParticipant.join(
                UUID.fromString("00000000-0000-0000-0000-000000000100"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                ParticipantRole.MEMBER
        );

        // when
        participant.prePersist();

        // then
        assertThat(participant.getJoinedAt()).isNotNull();
    }
}
