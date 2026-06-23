package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 게시글 생성 시 S3에 실제로 업로드되지 않은 파일 참조 시 발생 */
public class S3ObjectNotFoundException extends BusinessException {

    public S3ObjectNotFoundException() {
        super(FeedErrorCode.S3_OBJECT_NOT_FOUND);
    }
}
