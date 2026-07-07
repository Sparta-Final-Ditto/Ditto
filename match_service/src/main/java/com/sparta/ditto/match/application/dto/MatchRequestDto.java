package com.sparta.ditto.match.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "매칭 요청")
public record MatchRequestDto(
        @Schema(description = "성별 필터", example = "NONE", allowableValues = {"NONE", "MALE", "FEMALE"})
        String genderFilter,

        @Schema(description = "위치 필터 사용 여부", example = "false")
        Boolean locationFilterOn,

        @Schema(description = "최소 나이 (선택)", example = "20", nullable = true)
        Integer minAge,

        @Schema(description = "최대 나이 (선택)", example = "30", nullable = true)
        Integer maxAge
) {}