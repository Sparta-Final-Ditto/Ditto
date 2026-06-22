package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 이미지 업로드 개수 5장 초과 시 발생 */
public class ImageCountExceededException extends BusinessException {

    public ImageCountExceededException() {
        super(FeedErrorCode.IMAGE_COUNT_EXCEEDED);
    }
}