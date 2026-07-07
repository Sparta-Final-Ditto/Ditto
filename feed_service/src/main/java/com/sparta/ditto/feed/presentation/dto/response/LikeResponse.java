package com.sparta.ditto.feed.presentation.dto.response;

import com.sparta.ditto.feed.application.dto.result.LikeResult;

public record LikeResponse(int likeCount, boolean isLiked) {

    public static LikeResponse from(LikeResult result) {
        return new LikeResponse(result.likeCount(), result.isLiked());
    }
}
