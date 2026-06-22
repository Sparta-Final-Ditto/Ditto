package com.sparta.ditto.user.domain.user.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class EmailAlreadyExistsException extends BusinessException {

    public EmailAlreadyExistsException() {
        super(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
