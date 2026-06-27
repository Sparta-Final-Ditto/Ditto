package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.infrastructure.persistence.CommentRepositoryImpl;
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
@Import({CommentRepositoryImpl.class, PostRepositoryImpl.class})
class CommentRepositoryDeleteTest {

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

    // -------------------------------------------------------
    // 댓글 삭제 시 deleted_at, deleted_by 정상 마킹 (Soft Delete)
    // -------------------------------------------------------
    @Test
    @DisplayName("댓글 삭제(Soft Delete) 시 deleted_at, deleted_by가 정상 마킹됨")
    void softDelete_삭제시_deletedAt_deletedBy_정상_마킹() {
        // given
        Post post = savePost();
        UUID userId = UUID.randomUUID();
        Comment comment = commentRepository.save(new Comment(post.getId(), userId, "테스트 댓글"));

        // when
        comment.delete(userId);
        commentRepository.save(comment);

        // then - findByIdAndDeletedAtIsNull로는 조회 불가 (소프트 딜리트 필터)
        Optional<Comment> notFound = commentRepository.findByIdAndDeletedAtIsNull(comment.getId());
        assertThat(notFound).isEmpty();

        // 엔티티 상태에서 직접 마킹 값 검증
        assertThat(comment.getDeletedAt()).isNotNull();
        assertThat(comment.getDeletedBy()).isEqualTo(userId);
    }

    // -------------------------------------------------------
    // posts.comment_count 원자적 감소 (@Modifying @Query)
    // -------------------------------------------------------
    @Test
    @DisplayName("댓글 삭제 시 posts.comment_count가 @Modifying @Query로 원자적으로 1 감소")
    void decrementCommentCount_원자적으로_1_감소() {
        // given
        Post savedPost = savePost();
        UUID postId = savedPost.getId();
        postRepository.incrementCommentCount(postId); // comment_count = 1로 설정

        // when
        postRepository.decrementCommentCount(postId);

        // then
        Post updated = postRepository.findById(postId).orElseThrow();
        assertThat(updated.getCommentCount()).isEqualTo(0);
    }
}