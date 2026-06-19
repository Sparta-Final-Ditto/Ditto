package com.sparta.ditto.feed.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.feed.application.service.UploadUrlService;
import com.sparta.ditto.feed.presentation.dto.request.UploadUrlRequest;
import com.sparta.ditto.feed.presentation.dto.response.UploadUrlResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final UploadUrlService uploadUrlService;

    @PostMapping("/upload-url")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> getUploadUrl(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UploadUrlRequest request
    ) {
        UploadUrlResponse response = uploadUrlService.generateUploadUrls(request.files());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
