package com.sparta.ditto.chat.presentation;

import com.sparta.ditto.chat.application.room.ChatDirectRoomService;
import com.sparta.ditto.chat.application.room.dto.ChatDirectRoomCreateCommand;
import com.sparta.ditto.chat.application.room.dto.ChatDirectRoomResult;
import com.sparta.ditto.chat.presentation.dto.request.ChatDirectRoomCreateRequest;
import com.sparta.ditto.chat.presentation.dto.response.ChatDirectRoomResponse;
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
@RequestMapping("/chat/rooms")
public class ChatDirectRoomController {

    private final ChatDirectRoomService chatDirectRoomService;

    @PostMapping("/direct")
    public ResponseEntity<ChatDirectRoomResponse> createOrGetDirectRoom(
            // TODO: 인증 공통 모듈 확정 후 JWT 기반 사용자 ID 추출로 교체한다.
            @RequestHeader("X-User-Id") UUID requesterId,
            @Valid @RequestBody ChatDirectRoomCreateRequest request
    ) {
        ChatDirectRoomCreateCommand command =
                ChatDirectRoomCreateCommand.of(requesterId, request.targetUserId());
        ChatDirectRoomResult result = chatDirectRoomService.createOrGetDirectRoom(command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity
                .status(status)
                .body(ChatDirectRoomResponse.from(result));
    }
}
