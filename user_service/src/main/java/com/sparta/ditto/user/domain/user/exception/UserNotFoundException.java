package com.sparta.ditto.user.domain.user.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super(UserErrorCode.USER_NOT_FOUND);
    }
}
