package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.common.entity.BaseEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
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

    @Test
    @DisplayName("isUpdated - updatedAt이 createdAt과 같으면 false")
    void isUpdated_whenUpdatedAtEqualsCreatedAt_returnsFalse() {
        Comment comment = new Comment(UUID.randomUUID(), UUID.randomUUID(), "내용");
        Instant now = Instant.now();
        ReflectionTestUtils.setField(comment, "createdAt", now);
        ReflectionTestUtils.setField(comment, "updatedAt", now);

        assertThat(comment.isUpdated()).isFalse();
    }

    @Test
    @DisplayName("isUpdated - updatedAt이 createdAt보다 이후이면 true")
    void isUpdated_whenUpdatedAtAfterCreatedAt_returnsTrue() {
        Comment comment = new Comment(UUID.randomUUID(), UUID.randomUUID(), "내용");
        Instant createdAt = Instant.now();
        Instant updatedAt = createdAt.plusSeconds(1);
        ReflectionTestUtils.setField(comment, "createdAt", createdAt);
        ReflectionTestUtils.setField(comment, "updatedAt", updatedAt);

        assertThat(comment.isUpdated()).isTrue();
    }
}