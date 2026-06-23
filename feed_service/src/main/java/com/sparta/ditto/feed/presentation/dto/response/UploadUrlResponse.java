package com.sparta.ditto.feed.presentation.dto.response;

import com.sparta.ditto.feed.application.dto.UploadUrlResult;
import java.util.List;

public record UploadUrlResponse(List<FileResponse> files) {
    public record FileResponse(String presignedUrl, String s3Key) {}

    public static UploadUrlResponse from(UploadUrlResult result) {
        List<FileResponse> files = result.files().stream()
                .map(f -> new FileResponse(f.presignedUrl(), f.s3Key()))
                .toList();
        return new UploadUrlResponse(files);
    }
}
