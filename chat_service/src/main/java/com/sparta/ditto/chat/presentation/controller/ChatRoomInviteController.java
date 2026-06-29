package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.room.ChatRoomInviteService;
import com.sparta.ditto.chat.application.room.dto.command.ChatRoomInviteCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomInviteResult;
import com.sparta.ditto.chat.presentation.dto.request.ChatRoomInviteRequest;
import com.sparta.ditto.chat.presentation.dto.response.ChatRoomInviteResponse;
import com.sparta.ditto.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms")
public class ChatRoomInviteController {

    private final ChatRoomInviteService chatRoomInviteService;

    @PostMapping("/{roomId}/participants")
    public ResponseEntity<ApiResponse<ChatRoomInviteResponse>> inviteParticipants(
            // Gateway JWT 필터가 전달한 사용자 ID를 사용한다.
            @RequestHeader("X-User-Id") UUID requesterId,
            @PathVariable UUID roomId,
            @Valid @RequestBody ChatRoomInviteRequest request
    ) {
        ChatRoomInviteCommand command = ChatRoomInviteCommand.of(
                requesterId,
                roomId,
                request.userIds()
        );
        ChatRoomInviteResult result = chatRoomInviteService.invite(command);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(ChatRoomInviteResponse.from(result)));
    }
}
