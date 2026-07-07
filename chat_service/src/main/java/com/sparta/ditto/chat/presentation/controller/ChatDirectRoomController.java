package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.room.ChatDirectRoomService;
import com.sparta.ditto.chat.application.room.dto.command.ChatDirectRoomCreateCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatDirectRoomResult;
import com.sparta.ditto.chat.presentation.dto.request.ChatDirectRoomCreateRequest;
import com.sparta.ditto.chat.presentation.dto.response.ChatDirectRoomResponse;
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
public class ChatDirectRoomController {

    private final ChatDirectRoomService chatDirectRoomService;

    @PostMapping("/direct")
    public ResponseEntity<ApiResponse<ChatDirectRoomResponse>> createOrGetDirectRoom(
            // Gateway JWT 필터가 전달한 사용자 ID를 사용한다.
            @RequestHeader("X-User-Id") UUID requesterId,
            @Valid @RequestBody ChatDirectRoomCreateRequest request
    ) {
        ChatDirectRoomCreateCommand command =
                ChatDirectRoomCreateCommand.of(requesterId, request.targetUserId());
        ChatDirectRoomResult result = chatDirectRoomService.createOrGetDirectRoom(command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity
                .status(status)
                .body(responseBody(result));
    }

    private ApiResponse<ChatDirectRoomResponse> responseBody(ChatDirectRoomResult result) {
        ChatDirectRoomResponse response = ChatDirectRoomResponse.from(result);
        if (result.created()) {
            return ApiResponse.created(response);
        }
        return ApiResponse.success(response);
    }
}
