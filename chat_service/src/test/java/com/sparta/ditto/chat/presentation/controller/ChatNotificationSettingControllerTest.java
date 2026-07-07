package com.sparta.ditto.chat.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.chat.application.room.ChatNotificationSettingService;
import com.sparta.ditto.chat.application.room.dto.result.ChatNotificationSettingResult;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatNotificationSettingController.class)
@ActiveProfiles("test")
@DisplayName("ChatNotificationSettingController 테스트")
class ChatNotificationSettingControllerTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatNotificationSettingService chatNotificationSettingService;

    @Test
    @DisplayName("알림 설정 변경 API는 200 OK와 공통 수정 응답을 반환한다")
    void updateNotificationSetting_should_return_updated_response() throws Exception {
        // given
        given(chatNotificationSettingService.updateNotificationSetting(any()))
                .willReturn(ChatNotificationSettingResult.of(ROOM_ID, false));

        // when & then
        mockMvc.perform(patch("/api/v1/chat/rooms/{roomId}/notifications", ROOM_ID)
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("UPDATED"))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID.toString()))
                .andExpect(jsonPath("$.data.notificationEnabled").value(false));
    }

    @Test
    @DisplayName("enabled가 누락되면 400 Bad Request를 반환한다")
    void updateNotificationSetting_should_return_bad_request_when_enabled_is_missing()
            throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/chat/rooms/{roomId}/notifications", ROOM_ID)
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String requestBody(boolean enabled) {
        return """
                {
                  "enabled": %s
                }
                """.formatted(enabled);
    }
}
