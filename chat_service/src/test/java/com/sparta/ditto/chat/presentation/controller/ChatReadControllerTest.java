package com.sparta.ditto.chat.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.chat.application.room.ChatReadService;
import com.sparta.ditto.chat.application.room.dto.result.ChatReadResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatReadController.class)
@ActiveProfiles("test")
@DisplayName("ChatReadController 테스트")
class ChatReadControllerTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final String LAST_READ_MESSAGE_ID =
            "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
    private static final Instant LAST_READ_AT =
            Instant.parse("2026-06-20T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatReadService chatReadService;

    @Test
    @DisplayName("읽음 처리 API는 200 OK와 공통 수정 응답을 반환한다")
    void updateReadState_should_return_updated_response() throws Exception {
        // given
        given(chatReadService.updateReadState(any()))
                .willReturn(ChatReadResult.of(
                        ROOM_ID,
                        LAST_READ_MESSAGE_ID,
                        LAST_READ_AT
                ));

        // when & then
        mockMvc.perform(patch("/api/v1/chat/rooms/{roomId}/read", ROOM_ID)
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("UPDATED"))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.data.lastReadMessageId")
                        .value(LAST_READ_MESSAGE_ID))
                .andExpect(jsonPath("$.data.lastReadAt").exists());
    }

    private String requestBody() {
        return """
                {
                  "lastReadMessageId": "%s"
                }
                """.formatted(LAST_READ_MESSAGE_ID);
    }
}
