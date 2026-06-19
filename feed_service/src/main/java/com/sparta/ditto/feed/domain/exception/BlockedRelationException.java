package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class BlockedRelationException extends BusinessException {

    public BlockedRelationException() {
        super(FeedErrorCode.BLOCKED_RELATION);
    }
}