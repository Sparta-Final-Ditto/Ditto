package com.sparta.ditto.user.presentation.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UserLocationUpdateRequest(

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
