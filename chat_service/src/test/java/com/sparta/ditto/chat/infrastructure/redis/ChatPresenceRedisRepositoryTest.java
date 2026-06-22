package com.sparta.ditto.chat.infrastructure.redis;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@DisplayName("ChatPresenceRedisRepository 테스트")
class ChatPresenceRedisRepositoryTest {

    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID OTHER_ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final String ACTIVE_ROOM_KEY = "active_room:user:" + USER_ID;

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ChatPresenceRedisRepository chatPresenceRedisRepository;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        chatPresenceRedisRepository = new ChatPresenceRedisRepository(stringRedisTemplate);
    }

    @Test
    @DisplayName("현재 active room이 요청 roomId와 같을 때만 삭제한다")
    void leaveRoomIfCurrent_success_same_room() {
        // given
        given(valueOperations.get(ACTIVE_ROOM_KEY)).willReturn(ROOM_ID.toString());

        // when
        chatPresenceRedisRepository.leaveRoomIfCurrent(USER_ID, ROOM_ID);

        // then
        verify(stringRedisTemplate).delete(ACTIVE_ROOM_KEY);
    }

    @Test
    @DisplayName("현재 active room이 다른 방이면 삭제하지 않는다")
    void leaveRoomIfCurrent_ignore_different_room() {
        // given
        given(valueOperations.get(ACTIVE_ROOM_KEY)).willReturn(OTHER_ROOM_ID.toString());

        // when
        chatPresenceRedisRepository.leaveRoomIfCurrent(USER_ID, ROOM_ID);

        // then
        verify(stringRedisTemplate, never()).delete(ACTIVE_ROOM_KEY);
    }
}
