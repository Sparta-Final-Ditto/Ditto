package com.sparta.ditto.feed.application.dto.query;

import java.util.UUID;

public record GetCommentsQuery(
        UUID postId,
        UUID requesterId,
        String requesterRole,
        UUID cursor,
        int size
) {
}
