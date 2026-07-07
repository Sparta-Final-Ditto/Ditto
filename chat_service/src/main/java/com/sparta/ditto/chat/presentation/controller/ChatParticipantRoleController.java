package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.room.ChatRoomOwnerTransferService;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomOwnerTransferResult;
import com.sparta.ditto.chat.presentation.dto.request.ChatParticipantRoleUpdateRequest;
import com.sparta.ditto.chat.presentation.dto.response.ChatRoomOwnerTransferResponse;
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
public class ChatParticipantRoleController {

    private final ChatRoomOwnerTransferService chatRoomOwnerTransferService;

    @PatchMapping("/{roomId}/participants/{userId}/role")
    public ResponseEntity<ApiResponse<ChatRoomOwnerTransferResponse>> updateParticipantRole(
            // Gateway JWT 필터가 전달한 사용자 ID를 사용
            @RequestHeader("X-User-Id") UUID requesterId,
            @PathVariable UUID roomId,
            @PathVariable UUID userId,
            @Valid @RequestBody ChatParticipantRoleUpdateRequest request
    ) {
        ChatRoomOwnerTransferResult result = chatRoomOwnerTransferService.transferOwner(
                requesterId, roomId, userId, request.role());
        return ResponseEntity.ok(
                ApiResponse.updated(ChatRoomOwnerTransferResponse.from(result)));
    }
}
