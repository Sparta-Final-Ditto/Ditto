package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ImageSizeExceededException extends BusinessException {

    public ImageSizeExceededException() {
        super(FeedErrorCode.IMAGE_SIZE_EXCEEDED);
    }
}