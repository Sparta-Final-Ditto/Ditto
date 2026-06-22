package com.sparta.ditto.feed.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LikeTest {

    @Test
    @DisplayName("Like 생성 - 필드 정상 저장")
    void createLike() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Like like = new Like(postId, userId);

        assertThat(like.getPostId()).isEqualTo(postId);
        assertThat(like.getUserId()).isEqualTo(userId);
    }
}