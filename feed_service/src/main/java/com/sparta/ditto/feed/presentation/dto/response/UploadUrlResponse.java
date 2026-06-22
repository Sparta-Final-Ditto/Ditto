package com.sparta.ditto.feed.presentation.dto.response;

import java.util.List;

/** 미디어 업로드 URL 발급 응답 DTO */
public record UploadUrlResponse(List<FileResponse> files) {
    public record FileResponse(String presignedUrl, String s3Key) {}
}
