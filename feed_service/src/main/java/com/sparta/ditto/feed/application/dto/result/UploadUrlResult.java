package com.sparta.ditto.feed.application.dto.result;

import java.util.List;

public record UploadUrlResult(List<FileResult> files) {
    public record FileResult(String presignedUrl, String s3Key) {}
}
