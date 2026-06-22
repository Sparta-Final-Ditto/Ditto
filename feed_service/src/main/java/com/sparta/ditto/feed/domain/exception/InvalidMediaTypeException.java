package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 허용되지 않은 파일 형식(jpeg·png·gif·webp·mp4 외) 업로드 시 발생 */
public class InvalidMediaTypeException extends BusinessException {

    public InvalidMediaTypeException() {
        super(FeedErrorCode.INVALID_MEDIA_TYPE);
    }
}