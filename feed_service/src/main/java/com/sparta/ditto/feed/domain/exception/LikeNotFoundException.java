package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 좋아요하지 않은 게시글에 좋아요 취소 시도 시 발생 */
public class LikeNotFoundException extends BusinessException {

    public LikeNotFoundException() {
        super(FeedErrorCode.LIKE_NOT_FOUND);
    }
}
