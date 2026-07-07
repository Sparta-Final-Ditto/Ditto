package com.sparta.ditto.user.presentation.dto.response;

public record UserUpdateResponse(
        String nickname,
        String bio,
        String profileImageUrl,
        AuthTokenResponse tokens
) {
}
