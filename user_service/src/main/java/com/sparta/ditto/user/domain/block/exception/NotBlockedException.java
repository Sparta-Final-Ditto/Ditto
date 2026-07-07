package com.sparta.ditto.user.domain.block.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class NotBlockedException extends BusinessException {

    public NotBlockedException() {
        super(BlockErrorCode.NOT_BLOCKED);
    }
}
