package com.sparta.ditto.feed.application.dto;

import com.sparta.ditto.feed.domain.entity.Like;
import java.util.List;
import java.util.UUID;

public record LikeListResult(
        List<LikeUserResult> users,
        int totalCount,
        String nextCursor,
        boolean hasNext
) {
    public record LikeUserResult(String userId, String nickname) {}

    public static LikeListResult of(List<Like> likes, int totalCount, UUID nextCursor, boolean hasNext) {
        List<LikeUserResult> users = likes.stream()
                .map(l -> new LikeUserResult(l.getUserId().toString(), l.getUserNickname()))
                .toList();
        return new LikeListResult(
                users,
                totalCount,
                nextCursor != null ? nextCursor.toString() : null,
                hasNext
        );
    }
}
