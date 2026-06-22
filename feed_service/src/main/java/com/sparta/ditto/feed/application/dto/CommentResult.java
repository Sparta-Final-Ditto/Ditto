package com.sparta.ditto.feed.application.dto;

import com.sparta.ditto.feed.domain.entity.Comment;
import java.time.Instant;
import java.util.UUID;

public record CommentResult(
        UUID commentId,
        UUID postId,
        UUID authorUserId,
        String authorNickname,
        String content,
        boolean isMyComment,
        boolean isDeletable,
        Instant createdAt
) {
    public static CommentResult fromCreation(Comment comment, String nickname) {
        return new CommentResult(
                comment.getId(),
                comment.getPostId(),
                comment.getUserId(),
                nickname,
                comment.getContent(),
                true,
                true,
                comment.getCreatedAt()
        );
    }
}
