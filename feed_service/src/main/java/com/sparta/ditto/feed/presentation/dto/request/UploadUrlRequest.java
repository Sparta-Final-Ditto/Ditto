package com.sparta.ditto.feed.presentation.dto.request;

import java.util.List;

public record UploadUrlRequest(List<FileRequest> files) {
    public record FileRequest(String fileName, String fileType, Long fileSize) {}
}
