package com.sparta.ditto.feed.application.dto.response;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostTag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 게시글 생성 응답 DTO */
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

    public static CreatePostResponse from(Post savedPost, String nickname, String cloudfrontDomain) {
        String domain = cloudfrontDomain.endsWith("/")
                ? cloudfrontDomain.substring(0, cloudfrontDomain.length() - 1)
                : cloudfrontDomain;
        List<MediaFileResponse> mediaFiles = savedPost.getMediaList().stream()
                .map(m -> new MediaFileResponse(
                        m.getS3Key(),
                        domain + "/" + m.getS3Key(),
                        m.getMediaType().name(),
                        m.getSortOrder()
                ))
                .toList();

        List<String> tags = savedPost.getTags().stream()
                .map(PostTag::getTag)
                .toList();

        String neighborhood = savedPost.getShowLocation() ? savedPost.getNeighborhood() : null;

        return new CreatePostResponse(
                savedPost.getId(),
                new AuthorResponse(savedPost.getUserId(), nickname),
                savedPost.getContent(),
                neighborhood,
                tags,
                mediaFiles,
                savedPost.getLikeCount(),
                false,
                savedPost.getCommentCount(),
                savedPost.getShowLocation(),
                savedPost.getCreatedAt()
        );
    }
}