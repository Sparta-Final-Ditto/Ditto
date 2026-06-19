package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 차단 관계에서 좋아요·댓글 등 상호작용 시도 시 발생 */
public class BlockedRelationException extends BusinessException {

    public BlockedRelationException() {
        super(FeedErrorCode.BLOCKED_RELATION);
    }
}