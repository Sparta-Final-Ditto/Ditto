package com.sparta.ditto.chat.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.chat.application.ChatDirectRoomService;
import com.sparta.ditto.chat.application.dto.ChatDirectRoomResult;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatDirectRoomController.class)
@ActiveProfiles("test")
@DisplayName("ChatDirectRoomController 테스트")
class ChatDirectRoomControllerTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatDirectRoomService chatDirectRoomService;

    @Test
    @DisplayName("신규 1:1 채팅방 생성 시 201 Created를 반환한다")
    void createOrGetDirectRoom_should_return_created_when_room_is_new() throws Exception {
        // given
        given(chatDirectRoomService.createOrGetDirectRoom(any()))
                .willReturn(ChatDirectRoomResult.of(ROOM_ID, RoomStatus.ACTIVE, true, false));

        // when & then
        mockMvc.perform(post("/api/v1/chat/rooms/direct")
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("CREATED"))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.data.status").value(RoomStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.data.reactivated").value(false));
    }

    @Test
    @DisplayName("기존 1:1 채팅방 반환 시 200 OK를 반환한다")
    void createOrGetDirectRoom_should_return_ok_when_room_already_exists() throws Exception {
        // given
        given(chatDirectRoomService.createOrGetDirectRoom(any()))
                .willReturn(ChatDirectRoomResult.of(ROOM_ID, RoomStatus.ACTIVE, false, false));

        // when & then
        mockMvc.perform(post("/api/v1/chat/rooms/direct")
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.data.status").value(RoomStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.data.reactivated").value(false));
    }

    @Test
    @DisplayName("기존 INACTIVE 1:1 채팅방 재활성화 시 200 OK와 reactivated true를 반환한다")
    void createOrGetDirectRoom_should_return_ok_and_reactivated_when_room_reactivated()
            throws Exception {
        // given
        given(chatDirectRoomService.createOrGetDirectRoom(any()))
                .willReturn(ChatDirectRoomResult.of(ROOM_ID, RoomStatus.ACTIVE, false, true));

        // when & then
        mockMvc.perform(post("/api/v1/chat/rooms/direct")
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.data.status").value(RoomStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.data.reactivated").value(true));
    }

    private String requestBody() {
        return """
                {
                  "targetUserId": "%s"
                }
                """.formatted(TARGET_USER_ID);
    }
}
