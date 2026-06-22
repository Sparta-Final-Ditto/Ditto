package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 이미 좋아요한 게시글에 중복 좋아요 시 발생 */
public class DuplicateLikeException extends BusinessException {

    public DuplicateLikeException() {
        super(FeedErrorCode.DUPLICATE_LIKE);
    }
}