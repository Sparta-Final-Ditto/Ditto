package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.LocationScope;
import com.sparta.ditto.feed.infrastructure.persistence.LikeRepositoryImpl;
import com.sparta.ditto.feed.infrastructure.persistence.PostRepositoryImpl;
import java.util.List;
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
@Import({LikeRepositoryImpl.class, PostRepositoryImpl.class})
class LikeRepositorySoftDeleteAllByPostIdTest {

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
    private LikeRepository likeRepository;

    @Autowired
    private PostRepository postRepository;

    private Post savePost() {
        return postRepository.save(new Post(
                UUID.randomUUID(), "닉네임", "내용", "서울 성동구",
                37.5563, 127.0374, LocationScope.PUBLIC, true));
    }

    @Test
    @DisplayName("postId에 속한 좋아요 전체 soft delete → 영향 건수 반환")
    void softDeleteAllByPostId_좋아요_전체소프트딜리트() {
        Post post = savePost();
        UUID postId = post.getId();
        UUID deleterId = UUID.randomUUID();
        likeRepository.save(new Like(postId, UUID.randomUUID(), "닉A"));
        likeRepository.save(new Like(postId, UUID.randomUUID(), "닉B"));

        int affected = likeRepository.softDeleteAllByPostId(postId, deleterId);

        assertThat(affected).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 soft delete된 좋아요는 건너뜀 → 영향 건수 0")
    void softDeleteAllByPostId_이미삭제된좋아요는_건너뜀() {
        Post post = savePost();
        UUID postId = post.getId();
        Like like = likeRepository.save(new Like(postId, UUID.randomUUID(), "닉C"));
        like.delete(UUID.randomUUID());
        likeRepository.save(like);

        int affected = likeRepository.softDeleteAllByPostId(postId, UUID.randomUUID());

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("다른 postId의 좋아요는 건드리지 않음")
    void softDeleteAllByPostId_다른postId_영향없음() {
        Post target = savePost();
        Post other = savePost();
        likeRepository.save(new Like(other.getId(), UUID.randomUUID(), "닉D"));

        int affected = likeRepository.softDeleteAllByPostId(target.getId(), UUID.randomUUID());

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("findLikesWithCursor — soft delete된 좋아요는 커서 조회 결과에서 제외")
    void findLikesWithCursor_소프트딜리트된좋아요_제외() {
        Post post = savePost();
        UUID postId = post.getId();
        Like active = likeRepository.save(new Like(postId, UUID.randomUUID(), "활성"));
        Like deleted = likeRepository.save(new Like(postId, UUID.randomUUID(), "삭제됨"));
        deleted.delete(UUID.randomUUID());
        likeRepository.save(deleted);

        List<Like> result = likeRepository.findLikesWithCursor(postId, null, null, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(active.getId());
    }
}