package com.sparta.ditto.match.application.dto;

import java.util.UUID;

public record UserProfileEmbeddingDto(
        UUID userId,
        float[] profileVector,
        boolean active,
        int recordCount
){}
