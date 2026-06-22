package com.sparta.ditto.feed.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.ditto.feed.application.dto.LikeListResult;
import java.util.List;
import java.util.UUID;

public record LikeListResponse(
        List<LikeUserResponse> users,
        int totalCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor,
        boolean hasNext
) {
    public record LikeUserResponse(String userId, String nickname) {}

    public static LikeListResponse from(LikeListResult result) {
        List<LikeUserResponse> users = result.users().stream()
                .map(u -> new LikeUserResponse(u.userId(), u.nickname()))
                .toList();
        return new LikeListResponse(users, result.totalCount(), result.nextCursor(), result.hasNext());
    }
}
