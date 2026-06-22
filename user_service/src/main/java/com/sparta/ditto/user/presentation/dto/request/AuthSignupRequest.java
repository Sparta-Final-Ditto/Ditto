package com.sparta.ditto.user.presentation.dto.request;

import com.sparta.ditto.user.domain.user.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthSignupRequest(

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8)
        String password,

        @NotBlank @Size(max = 50)
        String nickname,

        @NotNull
        Gender gender,

        @Pattern(regexp = "\\d{8}", message = "생년월일은 YYYYMMDD 형식이어야 합니다.")
        String birthdate
) {
}
