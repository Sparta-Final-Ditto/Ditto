package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class FilesEmptyException extends BusinessException {

    public FilesEmptyException() {
        super(FeedErrorCode.FILES_EMPTY);
    }
}