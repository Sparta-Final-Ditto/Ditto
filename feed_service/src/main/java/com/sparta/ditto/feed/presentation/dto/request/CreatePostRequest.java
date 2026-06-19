package com.sparta.ditto.feed.presentation.dto.request;

import java.util.List;

public record CreatePostRequest(
        String content,
        List<String> tags,
        Double latitude,
        Double longitude,
        String locationScope,
        Boolean showLocation,
        List<MediaFileRequest> mediaFiles
) {
    public record MediaFileRequest(String s3Key, String mediaType, Integer sortOrder) {}
}