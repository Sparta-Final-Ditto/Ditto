package com.sparta.ditto.feed.application.dto;

import java.util.UUID;

public record GetLikesQuery(UUID postId, UUID cursor, int size) {}
