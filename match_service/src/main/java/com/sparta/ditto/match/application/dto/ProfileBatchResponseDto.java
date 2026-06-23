package com.sparta.ditto.match.application.dto;

import java.util.List;

public record ProfileBatchResponseDto(
        List<UserProfileEmbeddingDto> profiles
) {}
