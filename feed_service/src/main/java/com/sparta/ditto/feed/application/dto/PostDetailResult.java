package com.sparta.ditto.feed.application.dto;

import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Post;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostDetailResult(
        boolean isMyPost,
        UUID postId,
        String content,
        int likeCount,
        int commentCount,
        int viewCount,
        List<MediaItem> media,
        List<CommentItem> comments
) {

    public record MediaItem(UUID id, String mediaUrl, String mediaType, int sortOrder) {}

    public record CommentItem(
            UUID commentId,
            String content,
            String userNickname,
            Instant createdAt,
            boolean isUpdated
    ) {}

    public static PostDetailResult from(
            Post post, UUID requesterId, List<Comment> comments, String cloudfrontDomain) {
        String domain = cloudfrontDomain != null && cloudfrontDomain.endsWith("/")
                ? cloudfrontDomain.substring(0, cloudfrontDomain.length() - 1)
                : cloudfrontDomain;

        List<MediaItem> mediaItems = post.getMediaList().stream()
                .map(m -> new MediaItem(
                        m.getId(),
                        domain + "/" + m.getS3Key(),
                        m.getMediaType().name(),
                        m.getSortOrder()
                ))
                .toList();

        List<CommentItem> commentItems = comments.stream()
                .map(c -> new CommentItem(
                        c.getId(),
                        c.getContent(),
                        c.getUserNickname(),
                        c.getCreatedAt(),
                        c.isUpdated()
                ))
                .toList();

        return new PostDetailResult(
                requesterId.equals(post.getUserId()),
                post.getId(),
                post.getContent(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                mediaItems,
                commentItems
        );
    }
}