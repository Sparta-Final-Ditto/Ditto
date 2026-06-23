package com.sparta.ditto.user.domain.block.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class BlockedUserException extends BusinessException {

    public BlockedUserException() {
        super(BlockErrorCode.BLOCKED_USER);
    }
}
