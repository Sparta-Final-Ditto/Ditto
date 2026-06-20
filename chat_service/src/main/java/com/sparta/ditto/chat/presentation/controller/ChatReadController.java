package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.room.ChatReadService;
import com.sparta.ditto.chat.application.room.dto.command.ChatReadCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatReadResult;
import com.sparta.ditto.chat.presentation.dto.request.ChatReadStateUpdateRequest;
import com.sparta.ditto.chat.presentation.dto.response.ChatReadResponse;
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
public class ChatReadController {

    private final ChatReadService chatReadService;

    @PatchMapping("/{roomId}/read")
    public ResponseEntity<ApiResponse<ChatReadResponse>> updateReadState(
            // Gateway JWT 필터가 전달한 사용자 ID를 사용한다.
            @RequestHeader("X-User-Id") UUID requesterId,
            @PathVariable UUID roomId,
            @Valid @RequestBody ChatReadStateUpdateRequest request
    ) {
        ChatReadCommand command = ChatReadCommand.of(
                requesterId,
                roomId,
                request.lastReadMessageId()
        );
        ChatReadResult result = chatReadService.updateReadState(command);

        return ResponseEntity.ok(ApiResponse.updated(ChatReadResponse.from(result)));
    }
}
