package com.sparta.ditto.feed.presentation.dto.response;

import com.sparta.ditto.feed.application.dto.result.UpdatePostDisplayResult;

public record UpdatePostDisplayResponse(
        String postId,
        Boolean showLocation,
        String visibility
) {
    public static UpdatePostDisplayResponse from(UpdatePostDisplayResult result) {
        return new UpdatePostDisplayResponse(
                result.postId().toString(),
                result.showLocation(),
                result.visibility()
        );
    }
}
