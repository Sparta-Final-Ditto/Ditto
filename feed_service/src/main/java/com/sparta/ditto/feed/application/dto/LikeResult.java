package com.sparta.ditto.feed.application.dto;

import com.sparta.ditto.feed.domain.entity.Post;

public record LikeResult(int likeCount, boolean isLiked) {

    public static LikeResult liked(Post post) {
        return new LikeResult(post.getLikeCount() + 1, true);
    }

    public static LikeResult unliked(Post post) {
        return new LikeResult(Math.max(post.getLikeCount() - 1, 0), false);
    }
}
