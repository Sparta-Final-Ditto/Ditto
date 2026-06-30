package com.sparta.ditto.notification.domain.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다.", 404);

    private final String code;
    private final String message;
    private final int status;
}