package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class LikeNotFoundException extends BusinessException {

    public LikeNotFoundException() {
        super(FeedErrorCode.LIKE_NOT_FOUND);
    }
}