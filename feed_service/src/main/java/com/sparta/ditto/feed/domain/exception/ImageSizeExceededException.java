package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 이미지 파일 크기 10MB 초과 시 발생 */
public class ImageSizeExceededException extends BusinessException {

    public ImageSizeExceededException() {
        super(FeedErrorCode.IMAGE_SIZE_EXCEEDED);
    }
}