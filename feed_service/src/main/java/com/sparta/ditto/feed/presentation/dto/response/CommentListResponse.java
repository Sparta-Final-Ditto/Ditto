package com.sparta.ditto.feed.presentation.dto.response;

import com.sparta.ditto.feed.application.dto.CommentListResult;
import java.util.List;
import java.util.UUID;

public record CommentListResponse(
        List<CommentResponse> comments,
        UUID nextCursor,
        boolean hasNext
) {
    public static CommentListResponse from(CommentListResult result) {
        return new CommentListResponse(
                result.comments().stream().map(CommentResponse::from).toList(),
                result.nextCursor(),
                result.hasNext()
        );
    }
}
