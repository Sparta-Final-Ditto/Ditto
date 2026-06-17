package com.sparta.ditto.chat.domain.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DirectChatPair 도메인 테스트")
class DirectChatPairTest {

    @Test
    @DisplayName("1:1 채팅방 사용자 ID를 항상 작은 값, 큰 값 순서로 정렬한다")
    void orderUserIds_should_sort_user_ids_for_direct_room_key() {
        // given
        UUID userAId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID userBId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        // when
        DirectChatPair.OrderedUserIds orderedUserIds = DirectChatPair.orderUserIds(userAId, userBId);

        // then
        assertThat(orderedUserIds.user1Id()).isEqualTo(userBId);
        assertThat(orderedUserIds.user2Id()).isEqualTo(userAId);
    }

    @Test
    @DisplayName("1:1 채팅방 생성 시 정렬된 사용자 ID를 저장한다")
    void create_should_save_ordered_user_ids() {
        // given
        UUID roomId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        UUID userAId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID userBId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        // when
        DirectChatPair directChatPair = DirectChatPair.create(roomId, userAId, userBId);

        // then
        assertThat(directChatPair.getRoomId()).isEqualTo(roomId);
        assertThat(directChatPair.getUser1Id()).isEqualTo(userBId);
        assertThat(directChatPair.getUser2Id()).isEqualTo(userAId);
    }

    @Test
    @DisplayName("같은 사용자끼리는 1:1 채팅방 pair를 만들 수 없다")
    void orderUserIds_should_reject_same_user() {
        // given
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        // when & then
        assertThatThrownBy(() -> DirectChatPair.orderUserIds(userId, userId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
