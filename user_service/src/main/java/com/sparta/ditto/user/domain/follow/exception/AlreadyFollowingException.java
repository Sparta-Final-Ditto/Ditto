package com.sparta.ditto.user.domain.follow.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class AlreadyFollowingException extends BusinessException {

    public AlreadyFollowingException() {
        super(FollowErrorCode.ALREADY_FOLLOWING);
    }
}
