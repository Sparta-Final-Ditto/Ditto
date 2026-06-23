package com.sparta.ditto.match.application.dto;

import com.sparta.ditto.match.domain.entity.MatchStatus;

public record MatchStatusRequestDto(
        MatchStatus status
) {}
