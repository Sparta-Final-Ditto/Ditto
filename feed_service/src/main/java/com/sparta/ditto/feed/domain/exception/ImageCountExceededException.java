package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ImageCountExceededException extends BusinessException {

    public ImageCountExceededException() {
        super(FeedErrorCode.IMAGE_COUNT_EXCEEDED);
    }
}