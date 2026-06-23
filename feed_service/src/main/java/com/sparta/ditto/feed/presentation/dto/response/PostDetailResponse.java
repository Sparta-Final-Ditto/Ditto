package com.sparta.ditto.feed.presentation.dto.response;

import com.sparta.ditto.feed.application.dto.PostDetailResult;
import com.sparta.ditto.feed.application.dto.PostDetailResult.CommentItem;
import com.sparta.ditto.feed.application.dto.PostDetailResult.MediaItem;
import java.time.Instant;
import java.util.List;

public record PostDetailResponse(
        boolean isMyPost,
        String postId,
        String content,
        int likeCount,
        int commentCount,
        List<MediaResponse> media,
        List<CommentResponse> comments
) {

    public record MediaResponse(String id, String mediaUrl, String mediaType, int sortOrder) {
        public static MediaResponse from(MediaItem item) {
            return new MediaResponse(
                    item.id().toString(),
                    item.mediaUrl(),
                    item.mediaType(),
                    item.sortOrder()
            );
        }
    }

    public record CommentResponse(
            String commentId,
            String content,
            String userNickname,
            Instant createdAt,
            boolean isUpdated
    ) {
        public static CommentResponse from(CommentItem item) {
            return new CommentResponse(
                    item.commentId().toString(),
                    item.content(),
                    item.userNickname(),
                    item.createdAt(),
                    item.isUpdated()
            );
        }
    }

    public static PostDetailResponse from(PostDetailResult result) {
        return new PostDetailResponse(
                result.isMyPost(),
                result.postId().toString(),
                result.content(),
                result.likeCount(),
                result.commentCount(),
                result.media().stream().map(MediaResponse::from).toList(),
                result.comments().stream().map(CommentResponse::from).toList()
        );
    }
}
