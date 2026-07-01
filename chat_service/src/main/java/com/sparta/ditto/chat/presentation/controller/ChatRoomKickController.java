package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.room.ChatRoomKickService;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomKickResult;
import com.sparta.ditto.chat.presentation.dto.response.ChatRoomKickResponse;
import com.sparta.ditto.common.response.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms")
public class ChatRoomKickController {

    private final ChatRoomKickService chatRoomKickService;

    @DeleteMapping("/{roomId}/participants/{userId}")
    public ResponseEntity<ApiResponse<ChatRoomKickResponse>> kickParticipant(
            // Gateway JWT 필터가 전달한 사용자 ID를 사용
            @RequestHeader("X-User-Id") UUID requesterId,
            @PathVariable UUID roomId,
            @PathVariable UUID userId
    ) {
        ChatRoomKickResult result = chatRoomKickService.kick(requesterId, roomId, userId);
        return ResponseEntity.ok(ApiResponse.success(ChatRoomKickResponse.from(result)));
    }
}
