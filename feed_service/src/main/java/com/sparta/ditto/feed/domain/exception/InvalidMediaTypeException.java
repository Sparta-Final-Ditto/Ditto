package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class InvalidMediaTypeException extends BusinessException {

    public InvalidMediaTypeException() {
        super(FeedErrorCode.INVALID_MEDIA_TYPE);
    }
}