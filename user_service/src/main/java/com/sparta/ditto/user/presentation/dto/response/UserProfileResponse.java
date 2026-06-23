package com.sparta.ditto.user.presentation.dto.response;

import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.enums.Gender;
import java.time.LocalDate;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String nickname,
        Gender gender,
        LocalDate birthdate,
        String profileImageUrl,
        String bio
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getGender(),
                user.getBirthdate(),
                user.getProfileImageUrl(),
                user.getBio()
        );
    }
}
