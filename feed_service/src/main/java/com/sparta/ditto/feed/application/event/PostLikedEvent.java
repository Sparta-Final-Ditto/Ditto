package com.sparta.ditto.feed.application.event;

import java.time.Instant;
import java.util.UUID;

public record PostLikedEvent(
        UUID postId,
        UUID likerId,
        UUID ownerId,
        Instant likedAt
) {}
