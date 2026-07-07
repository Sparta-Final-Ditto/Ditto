package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** content와 미디어가 모두 없는 게시글 생성 시 발생하는 예외 */
public class EmptyPostException extends BusinessException {

    public EmptyPostException() {
        super(FeedErrorCode.EMPTY_POST);
    }
}
