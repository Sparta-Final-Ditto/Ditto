package com.sparta.ditto.feed.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommentTest {

    @Test
    @DisplayName("Comment 생성 - 필드 정상 저장")
    void createComment() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Comment comment = new Comment(postId, userId, "댓글 내용");

        assertThat(comment.getPostId()).isEqualTo(postId);
        assertThat(comment.getUserId()).isEqualTo(userId);
        assertThat(comment.getContent()).isEqualTo("댓글 내용");
    }
}