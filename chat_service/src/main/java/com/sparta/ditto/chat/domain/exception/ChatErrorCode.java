package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    CHAT_ROOM_NOT_FOUND("CHAT-001", "채팅방을 찾을 수 없습니다.", 404),
    CHAT_NOT_PARTICIPANT("CHAT-002", "채팅방 참여자가 아닙니다.", 403),
    CHAT_ROOM_INACTIVE("CHAT-003", "비활성화된 채팅방입니다.", 409),
    CHAT_MESSAGE_NOT_FOUND("CHAT-004", "메시지를 찾을 수 없습니다.", 404),
    CHAT_MESSAGE_FORBIDDEN("CHAT-005", "메시지에 대한 권한이 없습니다.", 403),
    CHAT_MESSAGE_SAVE_FAILED("CHAT-006", "메시지 저장에 실패했습니다.", 500),
    CHAT_INVALID_PRESENCE_STATUS("CHAT-007", "잘못된 채팅 접속 상태입니다.", 400),
    CHAT_INVALID_DIRECT_TARGET("CHAT-008", "1:1 채팅 상대가 올바르지 않습니다.", 400),
    CHAT_BLOCKED_USER("CHAT-009", "차단 관계인 사용자와 채팅할 수 없습니다.", 403),
    CHAT_INVALID_GROUP_PARTICIPANTS("CHAT-010", "그룹 채팅방은 본인을 포함해 3명 이상이어야 합니다.", 400),
    CHAT_DUPLICATE_PROCESSING("CHAT-011", "이미 처리 중인 메시지입니다.", 409);

    private final String code;
    private final String message;
    private final int status;
}
