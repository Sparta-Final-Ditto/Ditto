package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 존재하지 않는 댓글 조회·삭제 시 발생 */
public class CommentNotFoundException extends BusinessException {

    public CommentNotFoundException() {
        super(FeedErrorCode.COMMENT_NOT_FOUND);
    }
}