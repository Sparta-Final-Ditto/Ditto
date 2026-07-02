package com.sparta.ditto.feed.application.event;

import java.time.Instant;
import java.util.UUID;

public record PostCommentedEvent(
        UUID postId,
        UUID commentId,
        UUID commenterId,
        String actorNickname,
        UUID ownerId,
        Instant commentedAt
) {}
