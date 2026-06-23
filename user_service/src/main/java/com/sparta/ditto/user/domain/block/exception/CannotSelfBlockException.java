package com.sparta.ditto.user.domain.block.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class CannotSelfBlockException extends BusinessException {

    public CannotSelfBlockException() {
        super(BlockErrorCode.CANNOT_SELF_BLOCK);
    }
}
