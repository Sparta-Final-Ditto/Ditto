package com.sparta.ditto.match.application.dto;

import java.util.List;
import java.util.UUID;

public record ActiveUserIdsDto(
        List<UUID> userIds,
        int count
) {}