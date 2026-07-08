package com.sparta.ditto.user.presentation.dto.response;

public record UserLocationUpdateResponse(
        Double latitude,
        Double longitude,
        String neighborhood
) {
}
