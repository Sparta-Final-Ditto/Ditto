package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.feed.domain.type.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostTagTest {

    @Test
    @DisplayName("PostTag 생성 - 필드 정상 저장")
    void createPostTag() {
        Post post = new Post(UUID.randomUUID(), null, null, null, 37.5, 127.0, Visibility.PUBLIC, true);

        PostTag tag = new PostTag(post, "여행");

        assertThat(tag.getPost()).isEqualTo(post);
        assertThat(tag.getTag()).isEqualTo("여행");
    }
}