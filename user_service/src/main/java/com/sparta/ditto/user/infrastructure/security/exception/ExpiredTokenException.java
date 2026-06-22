package com.sparta.ditto.user.infrastructure.security.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ExpiredTokenException extends BusinessException {

    public ExpiredTokenException() {
        super(AuthErrorCode.EXPIRED_TOKEN);
    }
}
