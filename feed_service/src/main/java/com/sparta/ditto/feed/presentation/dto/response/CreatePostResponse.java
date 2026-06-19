package com.sparta.ditto.feed.presentation.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreatePostResponse(
        UUID postId,
        AuthorResponse author,
        String content,
        String neighborhood,
        List<String> tags,
        List<MediaFileResponse> mediaFiles,
        int likeCount,
        boolean isLiked,
        int commentCount,
        boolean showLocation,
        Instant createdAt
) {
    public record AuthorResponse(UUID userId, String nickname) {}
    public record MediaFileResponse(String s3Key, String mediaUrl, String mediaType, int sortOrder) {}
}