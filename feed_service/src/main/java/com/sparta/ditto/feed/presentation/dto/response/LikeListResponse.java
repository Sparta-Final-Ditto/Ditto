package com.sparta.ditto.feed.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.ditto.feed.domain.entity.Like;
import java.util.List;
import java.util.UUID;

public record LikeListResponse(
        List<LikeUserResponse> users,
        int totalCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor,
        boolean hasNext
) {

    public record LikeUserResponse(String userId, String nickname) {}

    public static LikeListResponse of(List<Like> likes, int totalCount, UUID nextCursor, boolean hasNext) {
        List<LikeUserResponse> users = likes.stream()
                .map(l -> new LikeUserResponse(l.getUserId().toString(), l.getUserNickname()))
                .toList();
        return new LikeListResponse(
                users,
                totalCount,
                nextCursor != null ? nextCursor.toString() : null,
                hasNext
        );
    }
}