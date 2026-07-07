package com.sparta.ditto.user.domain.report.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    ALREADY_REPORTED("REPORT-001", "이미 신고한 사용자입니다.", 409),
    CANNOT_SELF_REPORT("REPORT-002", "자기 자신을 신고할 수 없습니다.", 400);

    private final String code;
    private final String message;
    private final int status;
}
