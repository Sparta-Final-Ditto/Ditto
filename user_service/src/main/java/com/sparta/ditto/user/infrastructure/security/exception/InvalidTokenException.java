package com.sparta.ditto.user.infrastructure.security.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class InvalidTokenException extends BusinessException {

    public InvalidTokenException() {
        super(AuthErrorCode.INVALID_TOKEN);
    }
}
