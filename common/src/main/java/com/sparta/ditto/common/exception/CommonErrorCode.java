package com.sparta.ditto.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT("COMMON-001", "입력값이 올바르지 않습니다.", 400),
    UNAUTHORIZED("COMMON-002", "인증이 필요합니다.", 401),
    FORBIDDEN("COMMON-003", "접근 권한이 없습니다.", 403),
    NOT_FOUND("COMMON-004", "요청한 리소스를 찾을 수 없습니다.", 404),
    INTERNAL_SERVER_ERROR("COMMON-005", "서버 내부 오류가 발생했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;
}