package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.room.ChatGroupRoomService;
import com.sparta.ditto.chat.application.room.dto.ChatGroupRoomCreateCommand;
import com.sparta.ditto.chat.application.room.dto.ChatGroupRoomResult;
import com.sparta.ditto.chat.presentation.dto.request.ChatGroupRoomCreateRequest;
import com.sparta.ditto.chat.presentation.dto.response.ChatGroupRoomResponse;
import com.sparta.ditto.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms")
public class ChatGroupRoomController {

    private final ChatGroupRoomService chatGroupRoomService;

    @PostMapping("/group")
    public ResponseEntity<ApiResponse<ChatGroupRoomResponse>> createGroupRoom(
            // TODO: 인증 공통 모듈 확정 후 JWT 기반 사용자 ID 추출로 교체한다.
            @RequestHeader("X-User-Id") UUID requesterId,
            @Valid @RequestBody ChatGroupRoomCreateRequest request
    ) {
        ChatGroupRoomCreateCommand command = ChatGroupRoomCreateCommand.of(
                requesterId,
                request.participantUserIds(),
                request.roomName()
        );
        ChatGroupRoomResult result = chatGroupRoomService.createGroupRoom(command);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(ChatGroupRoomResponse.from(result)));
    }
}
