package com.sparta.ditto.match.application.dto;

import java.util.UUID;

public record UserNeighborhoodDto(
        UUID id,
        String neighborhood
) {}
