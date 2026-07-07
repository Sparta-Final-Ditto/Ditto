package com.sparta.ditto.user.domain.follow.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class CannotSelfFollowException extends BusinessException {

    public CannotSelfFollowException() {
        super(FollowErrorCode.CANNOT_SELF_FOLLOW);
    }
}
