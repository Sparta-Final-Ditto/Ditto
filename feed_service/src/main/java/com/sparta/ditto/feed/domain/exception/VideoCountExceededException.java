package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class VideoCountExceededException extends BusinessException {

    public VideoCountExceededException() {
        super(FeedErrorCode.VIDEO_COUNT_EXCEEDED);
    }
}