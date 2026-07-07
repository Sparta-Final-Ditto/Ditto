package com.sparta.ditto.user.domain.user.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class NicknameAlreadyExistsException extends BusinessException {

    public NicknameAlreadyExistsException() {
        super(UserErrorCode.NICKNAME_ALREADY_EXISTS);
    }
}
