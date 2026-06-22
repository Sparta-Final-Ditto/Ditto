package com.sparta.ditto.feed.presentation.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 게시글 생성 요청 DTO */
public record CreatePostRequest(
        @Size(max = 500, message = "게시글 본문은 500자 이내로 입력해주세요.")
        String content,

        @NotEmpty(message = "태그는 최소 1개 이상 입력해주세요.")
        @Size(max = 10, message = "태그는 최대 10개까지 입력할 수 있습니다.")
        List<String> tags,

        @NotNull(message = "위치 정보는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도 값이 유효한 범위를 벗어났습니다.")
        @DecimalMax(value = "90.0", message = "위도 값이 유효한 범위를 벗어났습니다.")
        Double latitude,

        @NotNull(message = "위치 정보는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도 값이 유효한 범위를 벗어났습니다.")
        @DecimalMax(value = "180.0", message = "경도 값이 유효한 범위를 벗어났습니다.")
        Double longitude,

        String locationScope,
        Boolean showLocation,
        List<MediaFileRequest> mediaFiles
) {
    public record MediaFileRequest(String s3Key, String mediaType, Integer sortOrder) {}
}