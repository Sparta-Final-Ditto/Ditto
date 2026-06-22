package com.sparta.ditto.feed.presentation.dto.response;

import com.sparta.ditto.feed.application.dto.FeedItemResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedItemResponse(
        UUID postId,
        AuthorResponse author,
        String content,
        List<MediaFileResponse> mediaFiles,
        List<String> tags,
        String neighborhood,
        int likeCount,
        boolean isLiked,
        int commentCount,
        Instant createdAt
) {

    public record AuthorResponse(UUID userId, String nickname) {}

    public record MediaFileResponse(
            String s3Key, String mediaUrl, String mediaType, int sortOrder) {}

    public static FeedItemResponse from(FeedItemResult result) {
        List<MediaFileResponse> mediaFiles = result.mediaFiles().stream()
                .map(m -> new MediaFileResponse(
                        m.s3Key(), m.mediaUrl(), m.mediaType(), m.sortOrder()))
                .toList();
        return new FeedItemResponse(
                result.postId(),
                new AuthorResponse(result.authorUserId(), result.authorNickname()),
                result.content(),
                mediaFiles,
                result.tags(),
                result.neighborhood(),
                result.likeCount(),
                result.isLiked(),
                result.commentCount(),
                result.createdAt()
        );
    }
}
