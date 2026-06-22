package com.sparta.ditto.user.domain.user.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class InvalidPasswordException extends BusinessException {

    public InvalidPasswordException() {
        super(UserErrorCode.INVALID_PASSWORD);
    }
}
