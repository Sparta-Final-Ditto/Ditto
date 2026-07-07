package com.sparta.ditto.user.presentation.dto.request;

import com.sparta.ditto.user.domain.user.enums.Gender;
import com.sparta.ditto.user.presentation.dto.request.validation.ValidBirthdate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AuthSignupRequest(

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8)
        String password,

        @NotBlank @Size(max = 50)
        String nickname,

        @NotNull
        Gender gender,

        @NotNull @Past @ValidBirthdate
        LocalDate birthdate
) {
}
