package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class DuplicateLikeException extends BusinessException {

    public DuplicateLikeException() {
        super(FeedErrorCode.DUPLICATE_LIKE);
    }
}