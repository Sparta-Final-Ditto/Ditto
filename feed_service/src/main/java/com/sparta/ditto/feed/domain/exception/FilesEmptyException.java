package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 업로드 URL 발급 요청 시 파일을 선택하지 않은 경우 발생 */
public class FilesEmptyException extends BusinessException {

    public FilesEmptyException() {
        super(FeedErrorCode.FILES_EMPTY);
    }
}
