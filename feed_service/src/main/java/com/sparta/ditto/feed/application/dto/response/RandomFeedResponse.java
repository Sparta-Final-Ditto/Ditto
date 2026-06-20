package com.sparta.ditto.feed.application.dto.response;

import java.util.List;
import java.util.UUID;

/** 랜덤 피드 조회 응답 DTO */
public record RandomFeedResponse(
        List<FeedItemResponse> feeds,
        UUID nextCursor,
        boolean hasNext
) {}