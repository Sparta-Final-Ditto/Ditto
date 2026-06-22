package com.sparta.ditto.feed.application.dto;

import java.util.UUID;

public record CreateCommentCommand(
        UUID postId,
        UUID userId,
        String userNickname,
        String content
) {}
