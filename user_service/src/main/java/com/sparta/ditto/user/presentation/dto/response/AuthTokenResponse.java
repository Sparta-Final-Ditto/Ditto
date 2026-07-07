package com.sparta.ditto.user.presentation.dto.response;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken
) {
}
