package com.sparta.ditto.user.presentation.dto.request;

import com.sparta.ditto.user.domain.user.enums.Gender;
import com.sparta.ditto.user.presentation.dto.request.validation.ValidBirthdate;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
        LocalDate birthdate,

        @NotNull(message = "위치 정보는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도 값이 유효한 범위를 벗어났습니다.")
        @DecimalMax(value = "90.0", message = "위도 값이 유효한 범위를 벗어났습니다.")
        Double latitude,

        @NotNull(message = "위치 정보는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도 값이 유효한 범위를 벗어났습니다.")
        @DecimalMax(value = "180.0", message = "경도 값이 유효한 범위를 벗어났습니다.")
        Double longitude
) {
}
