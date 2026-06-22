package com.sparta.ditto.feed.application.dto.response;

import com.sparta.ditto.feed.domain.entity.Comment;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID commentId,
        UUID postId,
        AuthorResponse author,
        String content,
        boolean isMyComment,
        boolean canDelete,
        Instant createdAt
) {

    public record AuthorResponse(UUID userId, String nickname) {
    }

    public static CommentResponse fromCreation(Comment comment, String nickname) {
        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                new AuthorResponse(comment.getUserId(), nickname),
                comment.getContent(),
                true,
                true,
                comment.getCreatedAt()
        );
    }
}