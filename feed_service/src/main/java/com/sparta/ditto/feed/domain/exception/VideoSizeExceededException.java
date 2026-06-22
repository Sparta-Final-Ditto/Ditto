package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** 영상 파일 크기 170MB 초과 시 발생 */
public class VideoSizeExceededException extends BusinessException {

    public VideoSizeExceededException() {
        super(FeedErrorCode.VIDEO_SIZE_EXCEEDED);
    }
}