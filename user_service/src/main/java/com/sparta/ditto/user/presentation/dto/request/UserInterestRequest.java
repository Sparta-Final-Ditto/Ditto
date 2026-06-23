package com.sparta.ditto.user.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UserInterestRequest(

        @NotEmpty
        @Size(min = 1, max = 10)
        List<String> hashtags
) {
}
