package com.sparta.ditto.feed.application.dto.query;

import java.util.UUID;

public record GetRandomFeedQuery(
        UUID userId,
        UUID cursorPostId,
        int size
) {}
