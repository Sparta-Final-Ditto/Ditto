package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(FeedErrorCode.FORBIDDEN);
    }
}
