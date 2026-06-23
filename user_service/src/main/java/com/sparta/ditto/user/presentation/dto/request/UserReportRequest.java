package com.sparta.ditto.user.presentation.dto.request;

import com.sparta.ditto.user.domain.report.enums.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserReportRequest(

        @NotNull
        ReportType reportType,

        @Size(max = 500)
        String content
) {
}
