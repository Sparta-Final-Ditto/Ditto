package com.sparta.ditto.feed.application.dto;

import java.util.List;
import java.util.UUID;

public record CommentListResult(
        List<CommentResult> comments,
        UUID nextCursor,
        boolean hasNext
) {
}
