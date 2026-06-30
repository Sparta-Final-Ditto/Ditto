package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.infrastructure.persistence.CommentRepositoryImpl;
import com.sparta.ditto.feed.infrastructure.persistence.PostRepositoryImpl;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CommentRepositoryImpl.class, PostRepositoryImpl.class})
class CommentRepositoryListTest {

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
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    private Post savePost() {
        return postRepository.save(new Post(
                UUID.randomUUID(), "닉네임", "내용", "서울 성동구",
                37.5563, 127.0374, Visibility.PUBLIC, true));
    }

    @Test
    @DisplayName("댓글 여러 개 → 작성일 기준 오름차순(ASC) 정렬")
    void findByPostIdWithCursor_작성일_오름차순_정렬() throws InterruptedException {
        // given
        Post post = savePost();
        UUID postId = post.getId();
        UUID userId = UUID.randomUUID();

        Comment first = commentRepository.save(new Comment(postId, userId, "첫 번째 댓글"));
        Thread.sleep(50);
        Comment second = commentRepository.save(new Comment(postId, userId, "두 번째 댓글"));
        Thread.sleep(50);
        Comment third = commentRepository.save(new Comment(postId, userId, "세 번째 댓글"));

        // when
        List<Comment> result = commentRepository.findByPostIdWithCursor(postId, null, null, 20);

        // then - 작성일 ASC 순 (첫 번째 → 두 번째 → 세 번째)
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(first.getId());
        assertThat(result.get(1).getId()).isEqualTo(second.getId());
        assertThat(result.get(2).getId()).isEqualTo(third.getId());
    }

    @Test
    @DisplayName("소프트 딜리트된 댓글 → 조회 결과에서 자동 제외")
    void findByPostIdWithCursor_소프트딜리트_댓글_제외() {
        // given
        Post post = savePost();
        UUID postId = post.getId();
        UUID userId = UUID.randomUUID();

        Comment active = commentRepository.save(new Comment(postId, userId, "활성 댓글"));
        Comment deleted = commentRepository.save(new Comment(postId, userId, "삭제된 댓글"));
        deleted.delete(userId);
        commentRepository.save(deleted);

        // when
        List<Comment> result = commentRepository.findByPostIdWithCursor(postId, null, null, 20);

        // then - deleted_at이 있는 댓글은 제외
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(active.getId());
    }

    @Test
    @DisplayName("cursor 존재 시 cursor 이후 데이터만 ASC 방향으로 조회")
    void findByPostIdWithCursor_cursor_이후_데이터만_조회() throws InterruptedException {
        // given
        Post post = savePost();
        UUID postId = post.getId();
        UUID userId = UUID.randomUUID();

        Comment first = commentRepository.save(new Comment(postId, userId, "첫 번째 댓글"));
        Thread.sleep(50);
        Comment second = commentRepository.save(new Comment(postId, userId, "두 번째 댓글"));
        Thread.sleep(50);
        Comment third = commentRepository.save(new Comment(postId, userId, "세 번째 댓글"));

        // when - second를 cursor로 사용 (createdAt > second.createdAt 방향)
        List<Comment> result = commentRepository.findByPostIdWithCursor(
                postId, second.getCreatedAt(), second.getId(), 20);

        // then - second 이후인 third만 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(third.getId());
        // first와 second는 포함되지 않음을 간접 검증
        assertThat(result).noneMatch(c -> c.getId().equals(first.getId()));
        assertThat(result).noneMatch(c -> c.getId().equals(second.getId()));
    }
}
