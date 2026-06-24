package com.sparta.ditto.feed.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.ditto.feed.application.dto.UserPostItemResult;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserPostItemResponse(
        String postId,
        String thumbnailUrl,
        String mediaType,
        String contentSummary
) {

    public static UserPostItemResponse from(UserPostItemResult result) {
        return new UserPostItemResponse(
                result.postId().toString(),
                result.thumbnailUrl(),
                result.mediaType(),
                result.contentSummary()
        );
    }
}
