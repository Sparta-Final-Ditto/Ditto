package com.sparta.ditto.feed.application.dto;

import java.util.List;
import java.util.UUID;

public record CreatePostCommand(
        UUID userId,
        String content,
        List<String> tags,
        Double latitude,
        Double longitude,
        String locationScope,
        Boolean showLocation,
        List<MediaFileItem> mediaFiles
) {
    public record MediaFileItem(String s3Key, String mediaType, Integer sortOrder) {}
}
