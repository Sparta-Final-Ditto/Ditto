package com.sparta.ditto.match.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record UserProfileEmbeddingDto(
        @JsonProperty("user_id")
        UUID userId,

        @JsonProperty("profile_vector")
        float[] profileVector,  // V_batch

        @JsonProperty("today_vector")
        float[] todayVector,    // V_today (null 가능!)

        boolean active,

        @JsonProperty("record_count")
        int recordCount
) {}