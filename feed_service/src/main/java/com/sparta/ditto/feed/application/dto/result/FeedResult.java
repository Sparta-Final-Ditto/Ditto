package com.sparta.ditto.feed.application.dto.result;

import java.util.List;
import java.util.UUID;

public record FeedResult(
        List<FeedItemResult> feeds,
        UUID nextCursor,
        boolean hasNext
) {}
