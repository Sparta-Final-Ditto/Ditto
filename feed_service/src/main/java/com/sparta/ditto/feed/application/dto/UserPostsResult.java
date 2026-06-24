package com.sparta.ditto.feed.application.dto;

import java.util.List;
import java.util.UUID;

public record UserPostsResult(
        List<UserPostItemResult> posts,
        UUID nextCursor,
        boolean hasNext
) {}
