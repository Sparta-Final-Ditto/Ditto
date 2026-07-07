package com.sparta.ditto.feed.application.dto.query;

import java.util.UUID;

public record GetUserPostsQuery(
        UUID requesterId,
        UUID targetUserId,
        UUID cursor,
        int size
) {}
