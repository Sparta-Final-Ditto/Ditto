package com.sparta.ditto.feed.presentation.dto.response;

import java.util.List;

public record UploadUrlResponse(List<FileResponse> files) {
    public record FileResponse(String presignedUrl, String s3Key) {}
}
