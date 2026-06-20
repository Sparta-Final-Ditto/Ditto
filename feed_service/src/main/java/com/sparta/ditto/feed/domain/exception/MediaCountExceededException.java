package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class MediaCountExceededException extends BusinessException {

    public MediaCountExceededException() {
        super(FeedErrorCode.MEDIA_COUNT_EXCEEDED);
    }
}