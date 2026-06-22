package com.sparta.ditto.user.domain.block.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BlockErrorCode implements ErrorCode {

    ALREADY_BLOCKED("BLOCK-001", "이미 차단한 사용자입니다.", 409),
    NOT_BLOCKED("BLOCK-002", "차단하지 않은 사용자입니다.", 400),
    CANNOT_SELF_BLOCK("BLOCK-003", "자기 자신을 차단할 수 없습니다.", 400),
    BLOCKED_USER("BLOCK-004", "차단된 사용자입니다.", 403);

    private final String code;
    private final String message;
    private final int status;
}
