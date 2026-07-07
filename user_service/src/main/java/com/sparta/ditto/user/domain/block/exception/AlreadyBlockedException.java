package com.sparta.ditto.user.domain.block.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class AlreadyBlockedException extends BusinessException {

    public AlreadyBlockedException() {
        super(BlockErrorCode.ALREADY_BLOCKED);
    }
}
