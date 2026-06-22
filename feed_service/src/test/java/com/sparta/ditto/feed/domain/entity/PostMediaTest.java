package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.feed.domain.type.LocationScope;
import com.sparta.ditto.feed.domain.type.MediaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostMediaTest {

    @Test
    @DisplayName("PostMedia 생성 - 필드 정상 저장")
    void createPostMedia() {
        Post post = new Post(UUID.randomUUID(), null, null, null, 37.5, 127.0, LocationScope.PUBLIC, true);

        PostMedia media = new PostMedia(post, "images/test.jpg", MediaType.IMAGE, 0);

        assertThat(media.getPost()).isEqualTo(post);
        assertThat(media.getS3Key()).isEqualTo("images/test.jpg");
        assertThat(media.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(media.getSortOrder()).isZero();
    }
}