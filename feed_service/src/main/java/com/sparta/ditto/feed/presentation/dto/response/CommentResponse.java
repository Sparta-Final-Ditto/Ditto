package com.sparta.ditto.feed.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.ditto.feed.application.dto.result.CommentResult;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID commentId,
        UUID postId,
        AuthorResponse author,
        String content,
        boolean isMyComment,
        @JsonProperty("canDelete") boolean isDeletable,
        Instant createdAt
) {
    public record AuthorResponse(UUID userId, String nickname) {}

    public static CommentResponse from(CommentResult result) {
        return new CommentResponse(
                result.commentId(),
                result.postId(),
                new AuthorResponse(result.authorUserId(), result.authorNickname()),
                result.content(),
                result.isMyComment(),
                result.isDeletable(),
                result.createdAt()
        );
    }
}
