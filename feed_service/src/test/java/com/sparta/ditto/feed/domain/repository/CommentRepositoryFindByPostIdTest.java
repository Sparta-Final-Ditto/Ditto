package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.infrastructure.persistence.CommentRepositoryImpl;
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
@Import({CommentRepositoryImpl.class, PostRepositoryImpl.class})
class CommentRepositoryFindByPostIdTest {

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
    @DisplayName("findByPostIdAndDeletedAtIsNull - 삭제된 댓글은 결과에서 제외된다")
    void findByPostIdAndDeletedAtIsNull_삭제된댓글_제외() {
        // given
        Post post = savePost();
        UUID postId = post.getId();
        UUID userId = UUID.randomUUID();

        Comment active = commentRepository.save(new Comment(postId, userId, "활성 댓글"));
        Comment deleted = commentRepository.save(new Comment(postId, userId, "삭제된 댓글"));
        deleted.delete(userId);
        commentRepository.save(deleted);

        // when
        List<Comment> result = commentRepository.findByPostIdAndDeletedAtIsNull(postId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(active.getId());
        assertThat(result).noneMatch(c -> c.getId().equals(deleted.getId()));
    }

    @Test
    @DisplayName("findByPostIdAndDeletedAtIsNull - 활성 댓글만 있으면 전부 반환된다")
    void findByPostIdAndDeletedAtIsNull_활성댓글만_전부반환() {
        // given
        Post post = savePost();
        UUID postId = post.getId();
        UUID userId = UUID.randomUUID();

        Comment c1 = commentRepository.save(new Comment(postId, userId, "댓글 1"));
        Comment c2 = commentRepository.save(new Comment(postId, userId, "댓글 2"));

        // when
        List<Comment> result = commentRepository.findByPostIdAndDeletedAtIsNull(postId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Comment::getId)
                .containsExactlyInAnyOrder(c1.getId(), c2.getId());
    }

    @Test
    @DisplayName("findByPostIdAndDeletedAtIsNull - 댓글이 없으면 빈 목록 반환")
    void findByPostIdAndDeletedAtIsNull_댓글없음_빈목록() {
        // given
        Post post = savePost();

        // when
        List<Comment> result = commentRepository.findByPostIdAndDeletedAtIsNull(post.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByPostIdAndDeletedAtIsNull - 다른 게시글의 댓글은 포함되지 않는다")
    void findByPostIdAndDeletedAtIsNull_다른게시글_댓글_미포함() {
        // given
        Post post = savePost();
        Post otherPost = savePost();
        UUID userId = UUID.randomUUID();

        commentRepository.save(new Comment(post.getId(), userId, "내 게시글 댓글"));
        commentRepository.save(new Comment(otherPost.getId(), userId, "다른 게시글 댓글"));

        // when
        List<Comment> result = commentRepository.findByPostIdAndDeletedAtIsNull(post.getId());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPostId()).isEqualTo(post.getId());
    }
}