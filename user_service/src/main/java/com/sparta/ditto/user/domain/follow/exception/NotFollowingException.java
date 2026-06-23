package com.sparta.ditto.user.domain.follow.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class NotFollowingException extends BusinessException {

    public NotFollowingException() {
        super(FollowErrorCode.NOT_FOLLOWING);
    }
}
