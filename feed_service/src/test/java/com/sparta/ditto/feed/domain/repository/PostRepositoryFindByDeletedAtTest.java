package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.Visibility;
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
class PostRepositoryFindByDeletedAtTest {

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
                UUID.randomUUID(), "닉네임", "내용", "서울 성동구",
                37.5563, 127.0374, Visibility.PUBLIC, true));
    }

    @Test
    @DisplayName("삭제되지 않은 게시글 → Optional 반환")
    void findByIdAndDeletedAtIsNull_미삭제게시글_반환() {
        Post post = savePost();

        Optional<Post> result = postRepository.findByIdAndDeletedAtIsNull(post.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(post.getId());
    }

    @Test
    @DisplayName("soft delete된 게시글 → Optional.empty() 반환")
    void findByIdAndDeletedAtIsNull_삭제된게시글_빈결과() {
        Post post = savePost();
        post.delete(UUID.randomUUID());
        postRepository.save(post);

        Optional<Post> result = postRepository.findByIdAndDeletedAtIsNull(post.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 ID → Optional.empty() 반환")
    void findByIdAndDeletedAtIsNull_없는ID_빈결과() {
        Optional<Post> result = postRepository.findByIdAndDeletedAtIsNull(UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}