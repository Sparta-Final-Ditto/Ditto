package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.room.ChatNotificationSettingService;
import com.sparta.ditto.chat.application.room.dto.command.ChatNotificationSettingCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatNotificationSettingResult;
import com.sparta.ditto.chat.presentation.dto.request.ChatNotificationSettingRequest;
import com.sparta.ditto.chat.presentation.dto.response.ChatNotificationSettingResponse;
import com.sparta.ditto.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms")
public class ChatNotificationSettingController {

    private final ChatNotificationSettingService chatNotificationSettingService;

    @PatchMapping("/{roomId}/notifications")
    public ResponseEntity<ApiResponse<ChatNotificationSettingResponse>> updateNotificationSetting(
            // Gateway JWT 필터가 전달한 사용자 ID를 사용한다.
            @RequestHeader("X-User-Id") UUID requesterId,
            @PathVariable UUID roomId,
            @Valid @RequestBody ChatNotificationSettingRequest request
    ) {
        ChatNotificationSettingCommand command = ChatNotificationSettingCommand.of(
                requesterId,
                roomId,
                request.enabled()
        );
        ChatNotificationSettingResult result =
                chatNotificationSettingService.updateNotificationSetting(command);

        return ResponseEntity.ok(ApiResponse.updated(ChatNotificationSettingResponse.from(result)));
    }
}
