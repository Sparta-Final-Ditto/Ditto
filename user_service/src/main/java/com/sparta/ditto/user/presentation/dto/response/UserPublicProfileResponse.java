package com.sparta.ditto.user.presentation.dto.response;

import com.sparta.ditto.user.domain.user.User;
import java.util.UUID;

public record UserPublicProfileResponse(
        UUID id,
        String nickname,
        String profileImageUrl,
        String bio
) {
    public static UserPublicProfileResponse from(User user) {
        return new UserPublicProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getBio()
        );
    }
}