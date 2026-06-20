package com.sparta.ditto.match.application.dto;

public record MatchRequestDto(
        String genderFilter,      // 성별 필터 (NONE/MALE/FEMALE)
        Boolean locationFilterOn  // 위치 필터 on/off
) {}