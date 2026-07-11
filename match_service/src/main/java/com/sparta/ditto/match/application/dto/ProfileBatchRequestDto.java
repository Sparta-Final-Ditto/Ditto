package com.sparta.ditto.match.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public record ProfileBatchRequestDto(
        @JsonProperty("user_ids")
        List<UUID> userIds
) {}