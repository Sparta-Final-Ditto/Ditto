package com.sparta.ditto.feed.application.dto.result;

import java.util.List;
import java.util.UUID;

public record CommentListResult(
        List<CommentResult> comments,
        UUID nextCursor,
        boolean hasNext
) {
}
