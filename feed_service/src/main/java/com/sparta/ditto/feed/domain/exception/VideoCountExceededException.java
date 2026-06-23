package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 영상 업로드 개수 1개 초과 시 발생 */
public class VideoCountExceededException extends BusinessException {

    public VideoCountExceededException() {
        super(FeedErrorCode.VIDEO_COUNT_EXCEEDED);
    }
}
