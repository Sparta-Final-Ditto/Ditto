package com.sparta.ditto.user.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.user.application.ReportService;
import com.sparta.ditto.user.presentation.dto.request.UserReportRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/{userId}/report")
    public ResponseEntity<ApiResponse<Void>> report(
            @RequestHeader("X-User-Id") UUID reporterId,
            @PathVariable UUID userId,
            @Valid @RequestBody UserReportRequest request) {
        reportService.report(reporterId, userId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
