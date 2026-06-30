package com.sparta.ditto.notification.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class NotificationNotFoundException extends BusinessException {

    public NotificationNotFoundException() {
        super(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }
}