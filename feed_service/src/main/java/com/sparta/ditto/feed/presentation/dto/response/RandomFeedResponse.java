package com.sparta.ditto.feed.presentation.dto.response;

import com.sparta.ditto.feed.application.dto.result.FeedResult;
import java.util.List;
import java.util.UUID;

public record RandomFeedResponse(
        List<FeedItemResponse> feeds,
        UUID nextCursor,
        boolean hasNext
) {
    public static RandomFeedResponse from(FeedResult result) {
        List<FeedItemResponse> feeds = result.feeds().stream()
                .map(FeedItemResponse::from)
                .toList();
        return new RandomFeedResponse(feeds, result.nextCursor(), result.hasNext());
    }
}
