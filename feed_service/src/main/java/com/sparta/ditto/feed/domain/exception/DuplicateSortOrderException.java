package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 미디어 정렬 순서 중복 시 발생하는 예외 */
public class DuplicateSortOrderException extends BusinessException {

    public DuplicateSortOrderException() {
        super(FeedErrorCode.DUPLICATE_SORT_ORDER);
    }
}
