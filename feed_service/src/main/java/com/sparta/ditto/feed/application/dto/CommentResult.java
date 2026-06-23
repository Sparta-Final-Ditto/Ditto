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
        return of(comment, nickname, true, true);
    }

    public static CommentResult fromList(
            Comment comment, UUID requesterId, UUID postOwnerId, String requesterRole) {
        boolean isMyComment = requesterId.equals(comment.getUserId());
        boolean isDeletable = isMyComment
                || requesterId.equals(postOwnerId)
                || "ADMIN".equals(requesterRole);
        return of(comment, comment.getUserNickname(), isMyComment, isDeletable);
    }

    private static CommentResult of(
            Comment comment, String nickname, boolean isMyComment, boolean isDeletable) {
        return new CommentResult(
                comment.getId(),
                comment.getPostId(),
                comment.getUserId(),
                nickname,
                comment.getContent(),
                isMyComment,
                isDeletable,
                comment.getCreatedAt()
        );
    }
}
