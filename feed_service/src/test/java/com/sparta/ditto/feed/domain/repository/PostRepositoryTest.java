package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.LocationScope;
import com.sparta.ditto.feed.infrastructure.persistence.PostRepositoryImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostRepositoryImpl.class)
class PostRepositoryTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAwareImpl() {
            return Optional::empty;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PostRepository postRepository;

    private Post savePost() {
        return postRepository.save(new Post(
                UUID.randomUUID(), "닉네임", "내용", "서울 강남구",
                37.5, 127.0, LocationScope.PUBLIC, true));
    }

    @Test
    @DisplayName("incrementViewCount - DB의 view_count가 1 증가한다")
    void incrementViewCount_viewCount가_1_증가한다() {
        // given
        Post post = savePost();
        UUID postId = post.getId();

        // when
        postRepository.incrementViewCount(postId);

        // then
        Post updated = postRepository.findById(postId).orElseThrow();
        assertThat(updated.getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("incrementViewCount 연속 2회 호출 시 view_count가 2가 된다")
    void incrementViewCount_2회_호출시_2가_된다() {
        // given
        Post post = savePost();
        UUID postId = post.getId();

        // when
        postRepository.incrementViewCount(postId);
        postRepository.incrementViewCount(postId);

        // then
        Post updated = postRepository.findById(postId).orElseThrow();
        assertThat(updated.getViewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("신규 게시글의 view_count 초기값은 0이다")
    void newPost_viewCount_초기값은_0이다() {
        // given & when
        Post post = savePost();

        // then
        assertThat(post.getViewCount()).isZero();
    }
}