package com.sparta.ditto.feed.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.feed.application.dto.FeedResult;
import com.sparta.ditto.feed.application.dto.GetRandomFeedQuery;
import com.sparta.ditto.feed.application.dto.UploadUrlCommand;
import com.sparta.ditto.feed.application.dto.UploadUrlResult;
import com.sparta.ditto.feed.application.service.FeedService;
import com.sparta.ditto.feed.application.service.UploadUrlService;
import com.sparta.ditto.feed.presentation.dto.request.UploadUrlRequest;
import com.sparta.ditto.feed.presentation.dto.response.RandomFeedResponse;
import com.sparta.ditto.feed.presentation.dto.response.UploadUrlResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final UploadUrlService uploadUrlService;
    private final FeedService feedService;

    @PostMapping("/upload-url")
    public ResponseEntity<ApiResponse<UploadUrlResponse>> getUploadUrl(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UploadUrlRequest request
    ) {
        List<UploadUrlCommand.FileItem> fileItems = request.files() != null
                ? request.files().stream()
                        .map(f -> new UploadUrlCommand.FileItem(
                                f.fileName(), f.fileType(), f.fileSize()))
                        .toList()
                : List.of();
        UploadUrlResult result = uploadUrlService.generateUploadUrls(
                new UploadUrlCommand(fileItems));
        return ResponseEntity.ok(ApiResponse.success(UploadUrlResponse.from(result)));
    }

    @GetMapping("/random")
    public ResponseEntity<ApiResponse<RandomFeedResponse>> getRandomFeed(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int size
    ) {
        FeedResult result = feedService.getRandomFeed(new GetRandomFeedQuery(userId, cursor, size));
        return ResponseEntity.ok(ApiResponse.success(RandomFeedResponse.from(result)));
    }
}
