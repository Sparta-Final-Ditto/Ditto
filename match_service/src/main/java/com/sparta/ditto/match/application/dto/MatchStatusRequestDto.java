package com.sparta.ditto.match.application.dto;

import com.sparta.ditto.match.domain.entity.MatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "매칭 상태 변경 요청")
public record MatchStatusRequestDto(
        @Schema(description = "변경할 상태", example = "ACCEPTED", allowableValues = {"ACCEPTED", "REJECTED"})
        MatchStatus status
) {}