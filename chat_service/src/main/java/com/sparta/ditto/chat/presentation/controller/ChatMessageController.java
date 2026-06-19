package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.message.ChatMessageService;
import com.sparta.ditto.chat.presentation.dto.response.ChatMessageCursorResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chat/rooms")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    // 이전 메시지 조회. before 없으면 최신 페이지 반환.
    @GetMapping("/{roomId}/messages")
    public ChatMessageCursorResponse getPreviousMessages(
            @PathVariable UUID roomId,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) Integer size,
            // TODO: 인증 공통 모듈 확정 후 JWT 기반 사용자 ID 추출로 교체한다.
            @RequestHeader("X-User-Id") UUID requesterId
    ) {
        return chatMessageService.getPreviousMessages(roomId, before, size, requesterId);
    }
}
