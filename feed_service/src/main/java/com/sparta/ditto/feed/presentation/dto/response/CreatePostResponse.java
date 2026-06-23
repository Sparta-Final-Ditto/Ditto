package com.sparta.ditto.feed.presentation.dto.response;

import com.sparta.ditto.feed.application.dto.PostResult;
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

    public record MediaFileResponse(
            String s3Key, String mediaUrl, String mediaType, int sortOrder) {}

    public static CreatePostResponse from(PostResult result) {
        List<MediaFileResponse> mediaFiles = result.mediaFiles().stream()
                .map(m -> new MediaFileResponse(
                        m.s3Key(), m.mediaUrl(), m.mediaType(), m.sortOrder()))
                .toList();
        return new CreatePostResponse(
                result.postId(),
                new AuthorResponse(result.authorUserId(), result.authorNickname()),
                result.content(),
                result.neighborhood(),
                result.tags(),
                mediaFiles,
                result.likeCount(),
                result.isLiked(),
                result.commentCount(),
                result.showLocation(),
                result.createdAt()
        );
    }
}
