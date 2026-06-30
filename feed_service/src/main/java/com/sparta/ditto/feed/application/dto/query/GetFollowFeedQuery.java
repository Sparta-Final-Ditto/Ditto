package com.sparta.ditto.feed.application.dto.query;

import java.util.UUID;

public record GetFollowFeedQuery(
        UUID userId,
        UUID cursor,
        int size
) {}
