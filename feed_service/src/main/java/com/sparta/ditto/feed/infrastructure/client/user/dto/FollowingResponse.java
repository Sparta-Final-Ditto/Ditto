package com.sparta.ditto.feed.infrastructure.client.user.dto;

import java.util.List;
import java.util.UUID;

public record FollowingResponse(
        int status,
        String message,
        List<FollowingUser> data
) {
    public record FollowingUser(UUID id) {}
}