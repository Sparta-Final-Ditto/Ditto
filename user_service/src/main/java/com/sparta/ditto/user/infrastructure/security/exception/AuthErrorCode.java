package com.sparta.ditto.user.infrastructure.security.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_TOKEN("AUTH-001", "유효하지 않은 토큰입니다.", 401),
    EXPIRED_TOKEN("AUTH-002", "만료된 토큰입니다.", 401);

    private final String code;
    private final String message;
    private final int status;
}
