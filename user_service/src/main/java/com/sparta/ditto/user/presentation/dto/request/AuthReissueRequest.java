package com.sparta.ditto.user.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AuthReissueRequest(

        @NotBlank
        String refreshToken
) {
}
