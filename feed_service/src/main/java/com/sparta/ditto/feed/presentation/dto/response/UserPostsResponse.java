package com.sparta.ditto.feed.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.ditto.feed.application.dto.result.UserPostsResult;
import java.util.List;

public record UserPostsResponse(
        List<UserPostItemResponse> posts,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor,
        boolean hasNext
) {

    public static UserPostsResponse from(UserPostsResult result) {
        List<UserPostItemResponse> posts = result.posts().stream()
                .map(UserPostItemResponse::from)
                .toList();
        String nextCursor = result.nextCursor() != null ? result.nextCursor().toString() : null;
        return new UserPostsResponse(posts, nextCursor, result.hasNext());
    }
}
