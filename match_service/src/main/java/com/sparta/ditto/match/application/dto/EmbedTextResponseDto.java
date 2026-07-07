package com.sparta.ditto.match.application.dto;

import java.util.List;

public record EmbedTextResponseDto(
        List<Float> vector,
        int dimension
) {}
