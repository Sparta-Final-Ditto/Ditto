package com.sparta.ditto.user.domain.user.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class UserBannedException extends BusinessException {

    public UserBannedException() {
        super(UserErrorCode.USER_BANNED);
    }
}
