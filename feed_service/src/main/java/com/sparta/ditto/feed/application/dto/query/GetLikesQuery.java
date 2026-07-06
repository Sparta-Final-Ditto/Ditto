package com.sparta.ditto.feed.application.dto.query;

import java.util.UUID;

public record GetLikesQuery(UUID postId, UUID requesterId, UUID cursor, int size) {}
