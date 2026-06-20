package com.sparta.ditto.chat.presentation.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.chat.application.room.ChatRoomLeaveService;
import com.sparta.ditto.chat.application.room.dto.ChatRoomLeaveResult;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatRoomLeaveController.class)
@ActiveProfiles("test")
@DisplayName("ChatRoomLeaveController 테스트")
class ChatRoomLeaveControllerTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final String LAST_VISIBLE_MESSAGE_ID =
            "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
    private static final Instant LEFT_AT = Instant.parse("2026-06-20T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatRoomLeaveService chatRoomLeaveService;

    @Test
    @DisplayName("채팅방 나가기 시 200 OK와 공통 응답을 반환한다")
    void leaveRoom_should_return_success_response() throws Exception {
        // given
        given(chatRoomLeaveService.leaveRoom(REQUESTER_ID, ROOM_ID))
                .willReturn(ChatRoomLeaveResult.of(
                        ROOM_ID,
                        RoomStatus.INACTIVE,
                        LEFT_AT,
                        LAST_VISIBLE_MESSAGE_ID
                ));

        // when & then
        mockMvc.perform(post("/api/v1/chat/rooms/{roomId}/leave", ROOM_ID)
                        .header("X-User-Id", REQUESTER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.data.status").value(RoomStatus.INACTIVE.name()))
                .andExpect(jsonPath("$.data.leftAt").exists())
                .andExpect(jsonPath("$.data.lastVisibleMessageId")
                        .value(LAST_VISIBLE_MESSAGE_ID));
    }
}
