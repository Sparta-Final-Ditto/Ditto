package com.sparta.ditto.feed.application.dto;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostTag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostResult(
        UUID postId,
        UUID authorUserId,
        String authorNickname,
        String content,
        String neighborhood,
        List<String> tags,
        List<MediaFileResult> mediaFiles,
        int likeCount,
        boolean isLiked,
        int commentCount,
        boolean showLocation,
        Instant createdAt
) {
    public record MediaFileResult(String s3Key, String mediaUrl, String mediaType, int sortOrder) {}

    public static PostResult from(Post post, String nickname, String cloudfrontDomain) {
        String domain = cloudfrontDomain != null && cloudfrontDomain.endsWith("/")
                ? cloudfrontDomain.substring(0, cloudfrontDomain.length() - 1)
                : cloudfrontDomain;
        List<MediaFileResult> mediaFiles = post.getMediaList().stream()
                .map(m -> new MediaFileResult(
                        m.getS3Key(),
                        domain + "/" + m.getS3Key(),
                        m.getMediaType().name(),
                        m.getSortOrder()
                ))
                .toList();
        List<String> tags = post.getTags().stream()
                .map(PostTag::getTag)
                .toList();
        String neighborhood = post.getShowLocation() ? post.getNeighborhood() : null;
        return new PostResult(
                post.getId(),
                post.getUserId(),
                nickname,
                post.getContent(),
                neighborhood,
                tags,
                mediaFiles,
                post.getLikeCount(),
                false,
                post.getCommentCount(),
                post.getShowLocation(),
                post.getCreatedAt()
        );
    }
}
