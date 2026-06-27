package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FeedErrorCode implements ErrorCode {

    // 미디어 업로드 URL 발급 (POST /feeds/upload-url)
    FILES_EMPTY("VALIDATION_ERROR", "업로드할 파일을 선택해주세요.", 400),
    INVALID_MEDIA_TYPE("INVALID_MEDIA_TYPE", "허용되지 않은 파일 형식입니다.", 400),
    IMAGE_SIZE_EXCEEDED("MEDIA_SIZE_EXCEEDED", "이미지는 장당 10MB 이하로 업로드해주세요.", 400),
    VIDEO_SIZE_EXCEEDED("MEDIA_SIZE_EXCEEDED", "영상은 170MB 이하로 업로드해주세요.", 400),
    IMAGE_COUNT_EXCEEDED("VALIDATION_ERROR", "이미지는 최대 5장까지 업로드할 수 있습니다.", 400),
    VIDEO_COUNT_EXCEEDED("VALIDATION_ERROR", "영상은 최대 1개까지 업로드할 수 있습니다.", 400),
    MEDIA_COUNT_EXCEEDED("VALIDATION_ERROR", "이미지와 영상을 합쳐 최대 6개까지 업로드할 수 있습니다.", 400),

    // S3 객체 검증
    S3_OBJECT_NOT_FOUND("S3_OBJECT_NOT_FOUND", "업로드된 파일을 찾을 수 없습니다.", 400),

    // 게시글 생성 입력값 검증 (POST /posts)
    EMPTY_POST("VALIDATION_ERROR", "이미지, 영상, 텍스트 중 하나는 반드시 입력해주세요.", 400),
    INVALID_VISIBILITY("VALIDATION_ERROR",
            "공개 범위는 PUBLIC, FOLLOWERS_ONLY, PRIVATE 중 선택해주세요.", 400),
    INVALID_POST_MEDIA_TYPE("VALIDATION_ERROR", "미디어 타입은 IMAGE, VIDEO 중 선택해주세요.", 400),
    DUPLICATE_SORT_ORDER("VALIDATION_ERROR", "미디어 파일의 정렬 순서가 중복됩니다.", 400),

    // 게시글 조회/상호작용
    POST_NOT_FOUND("POST_NOT_FOUND", "게시글을 찾을 수 없습니다.", 404),
    BLOCKED_RELATION("BLOCKED_RELATION", "차단 관계에서는 이 작업을 수행할 수 없습니다.", 403),

    // 좋아요
    DUPLICATE_LIKE("DUPLICATE_LIKE", "이미 좋아요를 누른 게시글입니다.", 409),
    LIKE_NOT_FOUND("LIKE_NOT_FOUND", "좋아요를 누르지 않은 게시글입니다.", 404),

    // 댓글
    COMMENT_NOT_FOUND("COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다.", 404),
    FORBIDDEN("FORBIDDEN", "권한이 없습니다.", 403);

    private final String code;
    private final String message;
    private final int status;
}
