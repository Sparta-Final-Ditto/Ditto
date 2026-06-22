package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 이미지·영상 합산 6개 초과 업로드 시 발생 */
public class MediaCountExceededException extends BusinessException {

    public MediaCountExceededException() {
        super(FeedErrorCode.MEDIA_COUNT_EXCEEDED);
    }
}