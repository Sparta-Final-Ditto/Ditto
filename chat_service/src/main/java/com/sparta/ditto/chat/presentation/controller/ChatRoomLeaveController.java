package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.room.ChatRoomLeaveService;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomLeaveResult;
import com.sparta.ditto.chat.presentation.dto.response.ChatRoomLeaveResponse;
import com.sparta.ditto.common.response.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms")
public class ChatRoomLeaveController {

    private final ChatRoomLeaveService chatRoomLeaveService;

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<ChatRoomLeaveResponse>> leaveRoom(
            // Gateway JWT 필터가 전달한 사용자 ID를 사용한다.
            @RequestHeader("X-User-Id") UUID requesterId,
            @PathVariable UUID roomId
    ) {
        ChatRoomLeaveResult result = chatRoomLeaveService.leaveRoom(requesterId, roomId);
        return ResponseEntity.ok(ApiResponse.success(ChatRoomLeaveResponse.from(result)));
    }
}
