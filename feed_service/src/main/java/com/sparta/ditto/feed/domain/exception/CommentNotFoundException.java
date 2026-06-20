package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class CommentNotFoundException extends BusinessException {

    public CommentNotFoundException() {
        super(FeedErrorCode.COMMENT_NOT_FOUND);
    }
}