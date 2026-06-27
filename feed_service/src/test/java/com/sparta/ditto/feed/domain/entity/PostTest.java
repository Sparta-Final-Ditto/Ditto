package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.feed.domain.type.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostTest {

    @Test
    @DisplayName("게시글 생성 - 모든 필드 정상 입력")
    void createPost_withAllFields() {
        UUID userId = UUID.randomUUID();

        Post post = new Post(userId, "테스트닉네임", "내용", "강남구", 37.5, 127.0, Visibility.PUBLIC, true);

        assertThat(post.getUserId()).isEqualTo(userId);
        assertThat(post.getContent()).isEqualTo("내용");
        assertThat(post.getNeighborhood()).isEqualTo("강남구");
        assertThat(post.getLatitude()).isEqualTo(37.5);
        assertThat(post.getLongitude()).isEqualTo(127.0);
        assertThat(post.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(post.getShowLocation()).isTrue();
        assertThat(post.getLikeCount()).isZero();
        assertThat(post.getCommentCount()).isZero();
    }

    @Test
    @DisplayName("게시글 생성 - locationScope null이면 PUBLIC 기본값")
    void createPost_nullLocationScope_defaultsToPublic() {
        Post post = new Post(UUID.randomUUID(), null, null, null, 37.5, 127.0, null, null);

        assertThat(post.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(post.getShowLocation()).isTrue();
    }

    @Test
    @DisplayName("좋아요 수 증가")
    void incrementLikeCount() {
        Post post = new Post(UUID.randomUUID(), null, null, null, 37.5, 127.0, Visibility.PUBLIC, true);

        post.incrementLikeCount();
        post.incrementLikeCount();

        assertThat(post.getLikeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("좋아요 수 감소 - 0 이상일 때 감소")
    void decrementLikeCount_whenPositive() {
        Post post = new Post(UUID.randomUUID(), null, null, null, 37.5, 127.0, Visibility.PUBLIC, true);
        post.incrementLikeCount();

        post.decrementLikeCount();

        assertThat(post.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("좋아요 수 감소 - 0이면 감소하지 않음")
    void decrementLikeCount_whenZero_doesNotGoNegative() {
        Post post = new Post(UUID.randomUUID(), null, null, null, 37.5, 127.0, Visibility.PUBLIC, true);

        post.decrementLikeCount();

        assertThat(post.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("댓글 수 증가")
    void incrementCommentCount() {
        Post post = new Post(UUID.randomUUID(), null, null, null, 37.5, 127.0, Visibility.PUBLIC, true);

        post.incrementCommentCount();
        post.incrementCommentCount();

        assertThat(post.getCommentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("댓글 수 감소 - 0 이상일 때 감소")
    void decrementCommentCount_whenPositive() {
        Post post = new Post(UUID.randomUUID(), null, null, null, 37.5, 127.0, Visibility.PUBLIC, true);
        post.incrementCommentCount();

        post.decrementCommentCount();

        assertThat(post.getCommentCount()).isZero();
    }

    @Test
    @DisplayName("댓글 수 감소 - 0이면 감소하지 않음")
    void decrementCommentCount_whenZero_doesNotGoNegative() {
        Post post = new Post(UUID.randomUUID(), null, null, null, 37.5, 127.0, Visibility.PUBLIC, true);

        post.decrementCommentCount();

        assertThat(post.getCommentCount()).isZero();
    }

}