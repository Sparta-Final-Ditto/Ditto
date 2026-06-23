package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 유효하지 않은 미디어 타입 문자열 입력 시 발생하는 예외 */
public class InvalidPostMediaTypeException extends BusinessException {

    public InvalidPostMediaTypeException() {
        super(FeedErrorCode.INVALID_POST_MEDIA_TYPE);
    }
}
