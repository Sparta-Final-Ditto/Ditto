package com.sparta.ditto.match.application.dto;

import java.util.UUID;

public record UserPublicProfileDto(
        UUID id,
        String nickname,
        String profileImageUrl,
        String bio
) {}