package com.sparta.ditto.user.presentation.dto.request;

import jakarta.validation.constraints.Size;

public record UserUpdateRequest(

        @Size(max = 50)
        String nickname,

        @Size(max = 200)
        String bio,

        String profileImageUrl
) {
}
