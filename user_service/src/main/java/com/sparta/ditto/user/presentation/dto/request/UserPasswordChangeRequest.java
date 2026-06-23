package com.sparta.ditto.user.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserPasswordChangeRequest(

        @NotBlank
        String currentPassword,

        @NotBlank @Size(min = 8)
        String newPassword
) {
}
