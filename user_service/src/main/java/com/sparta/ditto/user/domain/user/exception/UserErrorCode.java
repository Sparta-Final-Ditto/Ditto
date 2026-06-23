package com.sparta.ditto.user.domain.user.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND("USER-001", "사용자를 찾을 수 없습니다.", 404),
    EMAIL_ALREADY_EXISTS("USER-002", "이미 사용 중인 이메일입니다.", 409),
    NICKNAME_ALREADY_EXISTS("USER-003", "이미 사용 중인 닉네임입니다.", 409),
    INVALID_PASSWORD("USER-004", "비밀번호가 올바르지 않습니다.", 401),
    USER_BANNED("USER-005", "정지된 사용자입니다.", 403);

    private final String code;
    private final String message;
    private final int status;
}
