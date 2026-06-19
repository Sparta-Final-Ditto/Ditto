package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedErrorCode implements ErrorCode {

    FILES_EMPTY("VALIDATION_ERROR", "업로드할 파일을 선택해주세요.", 400),
    INVALID_MEDIA_TYPE("INVALID_MEDIA_TYPE", "허용되지 않은 파일 형식입니다.", 400),
    IMAGE_SIZE_EXCEEDED("MEDIA_SIZE_EXCEEDED", "이미지는 장당 10MB 이하로 업로드해주세요.", 400),
    VIDEO_SIZE_EXCEEDED("MEDIA_SIZE_EXCEEDED", "영상은 170MB 이하로 업로드해주세요.", 400),
    IMAGE_COUNT_EXCEEDED("VALIDATION_ERROR", "이미지는 최대 5장까지 업로드할 수 있습니다.", 400),
    VIDEO_COUNT_EXCEEDED("VALIDATION_ERROR", "영상은 최대 1개까지 업로드할 수 있습니다.", 400),
    MEDIA_COUNT_EXCEEDED("VALIDATION_ERROR", "이미지와 영상을 합쳐 최대 6개까지 업로드할 수 있습니다.", 400),
    S3_OBJECT_NOT_FOUND("S3_OBJECT_NOT_FOUND", "업로드된 파일을 찾을 수 없습니다.", 400),
    POST_NOT_FOUND("POST_NOT_FOUND", "게시글을 찾을 수 없습니다.", 404),
    BLOCKED_RELATION("BLOCKED_RELATION", "차단 관계에서는 이 작업을 수행할 수 없습니다.", 403),
    DUPLICATE_LIKE("DUPLICATE_LIKE", "이미 좋아요를 누른 게시글입니다.", 409),
    LIKE_NOT_FOUND("LIKE_NOT_FOUND", "좋아요를 누르지 않은 게시글입니다.", 404),
    COMMENT_NOT_FOUND("COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다.", 404);

    private final String code;
    private final String message;
    private final int status;
}
