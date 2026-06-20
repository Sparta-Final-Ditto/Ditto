package com.sparta.ditto.chat.presentation.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.chat.application.room.ChatRoomQueryService;
import com.sparta.ditto.chat.application.room.dto.result.ChatParticipantResult;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomDetailResult;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomSummaryResult;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatRoomQueryController.class)
@ActiveProfiles("test")
@DisplayName("ChatRoomQueryController 테스트")
class ChatRoomQueryControllerTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID OLD_ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatRoomQueryService chatRoomQueryService;

    @Test
    @DisplayName("채팅방 상세 조회 시 200 OK와 방 상세 정보를 반환한다")
    void getRoom_should_return_room_detail() throws Exception {
        // given
        ChatRoomDetailResult result = ChatRoomDetailResult.of(
                ROOM_ID,
                RoomType.GROUP,
                "스터디방",
                RoomStatus.ACTIVE,
                List.of(ChatParticipantResult.of(
                        REQUESTER_ID,
                        ParticipantRole.OWNER,
                        Instant.parse("2026-06-20T00:00:00Z"),
                        null
                )),
                true
        );
        given(chatRoomQueryService.getRoom(REQUESTER_ID, ROOM_ID)).willReturn(result);

        // when & then
        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}", ROOM_ID)
                        .header("X-User-Id", REQUESTER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.data.roomType").value(RoomType.GROUP.name()))
                .andExpect(jsonPath("$.data.roomName").value("스터디방"))
                .andExpect(jsonPath("$.data.status").value(RoomStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.data.notificationEnabled").value(true))
                .andExpect(jsonPath("$.data.participants[0].userId")
                        .value(REQUESTER_ID.toString()))
                .andExpect(jsonPath("$.data.participants[0].role")
                        .value(ParticipantRole.OWNER.name()));
    }

    @Test
    @DisplayName("내 채팅방 목록 조회 시 200 OK와 방 요약 목록을 반환한다")
    void getMyRooms_should_return_room_summaries() throws Exception {
        // given
        given(chatRoomQueryService.getMyRooms(REQUESTER_ID))
                .willReturn(List.of(
                        ChatRoomSummaryResult.of(
                                ROOM_ID,
                                RoomType.GROUP,
                                "스터디방",
                                null,
                                Instant.parse("2026-06-20T01:00:00Z"),
                                0L,
                                true
                        ),
                        ChatRoomSummaryResult.of(
                                OLD_ROOM_ID,
                                RoomType.DIRECT,
                                null,
                                null,
                                null,
                                0L,
                                false
                        )
                ));

        // when & then
        mockMvc.perform(get("/api/v1/chat/rooms")
                        .header("X-User-Id", REQUESTER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.data[0].roomType").value(RoomType.GROUP.name()))
                .andExpect(jsonPath("$.data[0].roomName").value("스터디방"))
                .andExpect(jsonPath("$.data[0].lastMessage").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data[0].lastMessageAt").exists())
                .andExpect(jsonPath("$.data[0].unreadCount").value(0))
                .andExpect(jsonPath("$.data[0].notificationEnabled").value(true))
                .andExpect(jsonPath("$.data[1].roomId").value(OLD_ROOM_ID.toString()))
                .andExpect(jsonPath("$.data[1].notificationEnabled").value(false));
    }

    @Test
    @DisplayName("참여 중인 채팅방이 없으면 빈 목록을 반환한다")
    void getMyRooms_should_return_empty_list() throws Exception {
        // given
        given(chatRoomQueryService.getMyRooms(OTHER_USER_ID)).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/v1/chat/rooms")
                        .header("X-User-Id", OTHER_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
