package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class S3ObjectNotFoundException extends BusinessException {

    public S3ObjectNotFoundException() {
        super(FeedErrorCode.S3_OBJECT_NOT_FOUND);
    }
}