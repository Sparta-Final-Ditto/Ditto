package com.sparta.ditto.feed.presentation.dto.response;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostTag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 피드 목록 조회 응답의 단건 게시글 DTO.
 * 랜덤·팔로우·매칭 피드 등 목록 API의 feeds 배열 원소로 사용된다.
 * showLocation=false이면 neighborhood를 null로 마스킹하고,
 * s3Key는 CloudFront URL로 변환하여 mediaUrl로 제공한다.
 */
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
    public record MediaFileResponse(String s3Key, String mediaUrl, String mediaType, int sortOrder) {}

    public static FeedItemResponse from(Post post, boolean isLiked, String cloudfrontDomain) {
        String domain = cloudfrontDomain.endsWith("/")
                ? cloudfrontDomain.substring(0, cloudfrontDomain.length() - 1)
                : cloudfrontDomain;
        String neighborhood = post.getShowLocation() ? post.getNeighborhood() : null;

        List<MediaFileResponse> mediaFiles = post.getMediaList().stream()
                .map(m -> new MediaFileResponse(
                        m.getS3Key(),
                        domain + "/" + m.getS3Key(),
                        m.getMediaType().name(),
                        m.getSortOrder()))
                .toList();

        List<String> tags = post.getTags().stream().map(PostTag::getTag).toList();

        return new FeedItemResponse(
                post.getId(),
                new AuthorResponse(post.getUserId(), post.getAuthorNickname()),
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