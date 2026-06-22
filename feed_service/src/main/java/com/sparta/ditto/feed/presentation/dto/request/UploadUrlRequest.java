package com.sparta.ditto.feed.presentation.dto.request;

import java.util.List;

/** 미디어 업로드 URL 발급 요청 DTO */
public record UploadUrlRequest(List<FileRequest> files) {
    public record FileRequest(String fileName, String fileType, Long fileSize) {}
}
