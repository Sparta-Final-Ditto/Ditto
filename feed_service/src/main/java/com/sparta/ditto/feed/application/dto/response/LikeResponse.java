package com.sparta.ditto.feed.application.dto.response;

import com.sparta.ditto.feed.domain.entity.Post;

public record LikeResponse(int likeCount, boolean isLiked) {

    public static LikeResponse liked(Post post) {
        return new LikeResponse(post.getLikeCount() + 1, true);
    }

    public static LikeResponse unliked(Post post) {
        return new LikeResponse(Math.max(post.getLikeCount() - 1, 0), false);
    }
}
