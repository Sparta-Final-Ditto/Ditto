package com.sparta.ditto.feed.application.event;

import java.time.Instant;
import java.util.UUID;

public record PostLikedEvent(
        UUID likeId,
        UUID postId,
        UUID likerId,
        String actorNickname,
        UUID ownerId,
        Instant likedAt
) {}
