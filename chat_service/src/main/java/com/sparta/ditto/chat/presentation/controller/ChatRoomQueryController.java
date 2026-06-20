package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.room.ChatRoomQueryService;
import com.sparta.ditto.chat.application.room.dto.ChatRoomDetailResult;
import com.sparta.ditto.chat.presentation.dto.response.ChatRoomResponse;
import com.sparta.ditto.chat.presentation.dto.response.ChatRoomSummaryResponse;
import com.sparta.ditto.common.response.ApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms")
public class ChatRoomQueryController {

    private final ChatRoomQueryService chatRoomQueryService;

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> getRoom(
            // TODO: 인증 공통 모듈 확정 후 JWT 기반 사용자 ID 추출로 교체한다.
            @RequestHeader("X-User-Id") UUID requesterId,
            @PathVariable UUID roomId
    ) {
        ChatRoomDetailResult result = chatRoomQueryService.getRoom(requesterId, roomId);

        return ResponseEntity.ok(ApiResponse.success(ChatRoomResponse.from(result)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatRoomSummaryResponse>>> getMyRooms(
            // TODO: 인증 공통 모듈 확정 후 JWT 기반 사용자 ID 추출로 교체한다.
            @RequestHeader("X-User-Id") UUID requesterId
    ) {
        List<ChatRoomSummaryResponse> responses = chatRoomQueryService.getMyRooms(requesterId)
                .stream()
                .map(ChatRoomSummaryResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
