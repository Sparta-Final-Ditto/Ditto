package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 존재하지 않는 게시글 조회·상호작용 시 발생 */
public class PostNotFoundException extends BusinessException {

    public PostNotFoundException() {
        super(FeedErrorCode.POST_NOT_FOUND);
    }
}
