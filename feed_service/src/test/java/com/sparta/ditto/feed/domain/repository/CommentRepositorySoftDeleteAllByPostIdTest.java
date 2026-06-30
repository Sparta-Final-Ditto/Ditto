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
class CommentRepositorySoftDeleteAllByPostIdTest {

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
    @DisplayName("postId에 속한 미삭제 댓글 전체 soft delete → 영향 건수 반환")
    void softDeleteAllByPostId_미삭제댓글_전체소프트딜리트() {
        Post post = savePost();
        UUID postId = post.getId();
        UUID deleterId = UUID.randomUUID();
        commentRepository.save(new Comment(postId, UUID.randomUUID(), "닉A", "댓글1"));
        commentRepository.save(new Comment(postId, UUID.randomUUID(), "닉B", "댓글2"));

        int affected = commentRepository.softDeleteAllByPostId(postId, deleterId);

        assertThat(affected).isEqualTo(2);
        List<Comment> remaining = commentRepository.findByPostIdAndDeletedAtIsNull(postId);
        assertThat(remaining).isEmpty();
    }

    @Test
    @DisplayName("이미 soft delete된 댓글은 건너뜀 → 영향 건수 0")
    void softDeleteAllByPostId_이미삭제된댓글은_건너뜀() {
        Post post = savePost();
        UUID postId = post.getId();
        UUID firstDeleterid = UUID.randomUUID();
        Comment comment = commentRepository.save(
                new Comment(postId, UUID.randomUUID(), "닉C", "댓글"));
        comment.delete(firstDeleterid);
        commentRepository.save(comment);

        int affected = commentRepository.softDeleteAllByPostId(postId, UUID.randomUUID());

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("다른 postId의 댓글은 건드리지 않음")
    void softDeleteAllByPostId_다른postId_댓글_영향없음() {
        Post target = savePost();
        Post other = savePost();
        commentRepository.save(
                new Comment(other.getId(), UUID.randomUUID(), "닉D", "다른 게시글 댓글"));

        int affected = commentRepository.softDeleteAllByPostId(target.getId(), UUID.randomUUID());

        assertThat(affected).isEqualTo(0);
        List<Comment> otherComments =
                commentRepository.findByPostIdAndDeletedAtIsNull(other.getId());
        assertThat(otherComments).hasSize(1);
    }
}
