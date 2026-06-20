package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class VideoSizeExceededException extends BusinessException {

    public VideoSizeExceededException() {
        super(FeedErrorCode.VIDEO_SIZE_EXCEEDED);
    }
}