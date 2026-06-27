package com.sparta.ditto.feed.application.dto.command;

import java.util.UUID;

public record UpdatePostDisplayCommand(
        UUID postId,
        UUID requesterId,
        String visibility,
        Boolean showLocation
) {}