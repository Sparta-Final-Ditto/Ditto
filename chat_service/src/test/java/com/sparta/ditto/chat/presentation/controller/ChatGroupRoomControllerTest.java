package com.sparta.ditto.chat.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.chat.application.room.ChatGroupRoomService;
import com.sparta.ditto.chat.application.room.dto.ChatGroupRoomResult;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatGroupRoomController.class)
@ActiveProfiles("test")
@DisplayName("ChatGroupRoomController 테스트")
class ChatGroupRoomControllerTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final String ROOM_NAME = "스터디 그룹";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatGroupRoomService chatGroupRoomService;

    @Test
    @DisplayName("그룹 채팅방 생성 시 201 Created와 공통 응답을 반환한다")
    void createGroupRoom_should_return_created_response() throws Exception {
        // given
        given(chatGroupRoomService.createGroupRoom(any()))
                .willReturn(ChatGroupRoomResult.of(
                        ROOM_ID,
                        RoomType.GROUP,
                        ROOM_NAME,
                        RoomStatus.ACTIVE
                ));

        // when & then
        mockMvc.perform(post("/api/v1/chat/rooms/group")
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("CREATED"))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.data.roomType").value(RoomType.GROUP.name()))
                .andExpect(jsonPath("$.data.roomName").value(ROOM_NAME))
                .andExpect(jsonPath("$.data.status").value(RoomStatus.ACTIVE.name()));
    }

    private String requestBody() {
        return """
                {
                  "participantUserIds": ["%s"],
                  "roomName": "%s"
                }
                """.formatted(MEMBER_USER_ID, ROOM_NAME);
    }
}
