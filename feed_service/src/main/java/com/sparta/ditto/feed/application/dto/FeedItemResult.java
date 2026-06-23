package com.sparta.ditto.feed.application.dto;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostTag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedItemResult(
        UUID postId,
        UUID authorUserId,
        String authorNickname,
        String content,
        List<MediaResult> mediaFiles,
        List<String> tags,
        String neighborhood,
        int likeCount,
        boolean isLiked,
        int commentCount,
        Instant createdAt
) {
    public record MediaResult(String s3Key, String mediaUrl, String mediaType, int sortOrder) {}

    public static FeedItemResult from(Post post, boolean isLiked, String cloudfrontDomain) {
        String domain = cloudfrontDomain != null && cloudfrontDomain.endsWith("/")
                ? cloudfrontDomain.substring(0, cloudfrontDomain.length() - 1)
                : cloudfrontDomain;
        String neighborhood = post.getShowLocation() ? post.getNeighborhood() : null;
        List<MediaResult> mediaFiles = post.getMediaList().stream()
                .map(m -> new MediaResult(
                        m.getS3Key(),
                        domain + "/" + m.getS3Key(),
                        m.getMediaType().name(),
                        m.getSortOrder()))
                .toList();
        List<String> tags = post.getTags().stream().map(PostTag::getTag).toList();
        return new FeedItemResult(
                post.getId(),
                post.getUserId(),
                post.getAuthorNickname(),
                post.getContent(),
                mediaFiles,
                tags,
                neighborhood,
                post.getLikeCount(),
                isLiked,
                post.getCommentCount(),
                post.getCreatedAt()
        );
    }
}
