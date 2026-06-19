package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class PostNotFoundException extends BusinessException {

    public PostNotFoundException() {
        super(FeedErrorCode.POST_NOT_FOUND);
    }
}