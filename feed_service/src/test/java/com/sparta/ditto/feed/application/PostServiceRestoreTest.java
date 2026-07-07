package com.sparta.ditto.feed.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.exception.ForbiddenException;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.infrastructure.kafka.OutboxEventAdapter;
import com.sparta.ditto.feed.infrastructure.persistence.CommentJpaRepository;
import com.sparta.ditto.feed.infrastructure.persistence.CommentRepositoryImpl;
import com.sparta.ditto.feed.infrastructure.persistence.LikeJpaRepository;
import com.sparta.ditto.feed.infrastructure.persistence.LikeRepositoryImpl;
import com.sparta.ditto.feed.infrastructure.persistence.OutboxEventJpaRepository;
import com.sparta.ditto.feed.infrastructure.persistence.OutboxEventRepositoryImpl;
import com.sparta.ditto.feed.infrastructure.persistence.PostJpaRepository;
import com.sparta.ditto.feed.infrastructure.persistence.PostRepositoryImpl;
import com.sparta.ditto.feed.support.PostgresTestContainerSupport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostService.restorePost 슬라이스 통합 테스트
 *
 * <p>@DataJpaTest는 JPA 슬라이스만 로드하므로 @Service·@Repository 빈은 자동 등록되지 않는다.
 * PostService와 각 RepositoryImpl·OutboxEventAdapter를 @Import로 명시하여
 * 도메인 Repository 인터페이스 → DIP 구현체 경로가 실제로 동작하도록 한다.
 * (PostHardDeleteServiceTest와 동일한 @DataJpaTest + @Import + Testcontainers 패턴)
 * PostService의 @Value("${app.cloudfront.domain}")를 충족하기 위해 TestPropertySource를 사용한다.
 * 데이터 셋업·검증에는 @DataJpaTest가 자동 등록하는 JpaRepository를 직접 사용한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "app.cloudfront.domain=https://test.example.com")
@Import({
    PostService.class,
    PostRepositoryImpl.class,
    CommentRepositoryImpl.class,
    LikeRepositoryImpl.class,
    OutboxEventRepositoryImpl.class,
    OutboxEventAdapter.class
})
class PostServiceRestoreTest extends PostgresTestContainerSupport {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAwareImpl() {
            return Optional::empty;
        }
    }

    @Autowired
    private PostService postService;

    // 데이터 셋업·검증용 (DataJpaTest가 자동 등록)
    @Autowired
    private PostJpaRepository postJpaRepository;
    @Autowired
    private CommentJpaRepository commentJpaRepository;
    @Autowired
    private LikeJpaRepository likeJpaRepository;
    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    private UUID authorId;
    private UUID savedPostId;
    private UUID cascadeCommentId;       // deletedByPostDeletion = true (복구 대상)
    private UUID directlyDeletedCommentId; // deletedByPostDeletion = false (복구 비대상)
    private UUID cascadeLikeId;           // deletedByPostDeletion = true (복구 대상)

    @BeforeEach
    void setUp() {
        outboxEventJpaRepository.deleteAll();
        likeJpaRepository.deleteAll();
        commentJpaRepository.deleteAll();
        postJpaRepository.deleteAll();

        authorId = UUID.randomUUID();

        // ── 게시글 생성 + soft delete ──────────────────────────────
        Post post = new Post(authorId, "닉네임", "내용", "서울 강남구",
                37.5, 127.0, Visibility.PUBLIC, true);
        Post savedPost = postJpaRepository.save(post);
        savedPostId = savedPost.getId();
        savedPost.delete(authorId);
        postJpaRepository.save(savedPost);

        // ── (1) cascade-deleted 댓글: 먼저 save, 나중에 softDeleteAllByPostId로 플래그 true ──
        Comment cascadeComment = commentJpaRepository.save(
                new Comment(savedPostId, UUID.randomUUID(), "닉네임A", "cascade 삭제 댓글"));
        cascadeCommentId = cascadeComment.getId();

        // ── (2) 직접-deleted 댓글: BaseEntity.delete() → deletedByPostDeletion은 false 유지 ──
        // softDeleteAllByPostId의 WHERE 조건이 "deletedAt IS NULL"이므로,
        // 먼저 직접 삭제해두면 cascade 쿼리 시 이 댓글은 제외된다.
        Comment directlyDeleted = commentJpaRepository.save(
                new Comment(savedPostId, UUID.randomUUID(), "닉네임B", "직접 삭제 댓글"));
        directlyDeletedCommentId = directlyDeleted.getId();
        directlyDeleted.delete(UUID.randomUUID());
        commentJpaRepository.save(directlyDeleted);

        // ── (3) cascade-deleted 좋아요: 먼저 save, 나중에 softDeleteAllByPostId로 플래그 true ──
        Like cascadeLike = likeJpaRepository.save(
                new Like(savedPostId, UUID.randomUUID(), "닉네임C"));
        cascadeLikeId = cascadeLike.getId();

        // ── cascade soft delete 실행 ─────────────────────────────────────────────────────
        // WHERE deletedAt IS NULL → (1) cascade 댓글, (3) cascade 좋아요만 대상
        //   → deletedAt 설정 + deletedByPostDeletion = true
        // (2) 직접 삭제 댓글은 이미 deletedAt != null 이므로 제외 → deletedByPostDeletion = false 유지
        commentJpaRepository.softDeleteAllByPostId(savedPostId, authorId, Instant.now());
        likeJpaRepository.softDeleteAllByPostId(savedPostId, authorId, Instant.now());
    }

    // ── 001-1 ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("게시글 + cascade 삭제된 댓글·좋아요 복구 시 deletedAt/deletedBy가 모두 null")
    void restorePost_게시글과_cascade_연관데이터_복구() {
        postService.restorePost(savedPostId, authorId, "USER");

        Post restoredPost = postJpaRepository.findById(savedPostId).orElseThrow();
        assertThat(restoredPost.getDeletedAt()).isNull();
        assertThat(restoredPost.getDeletedBy()).isNull();

        Comment restoredComment = commentJpaRepository.findById(cascadeCommentId).orElseThrow();
        assertThat(restoredComment.getDeletedAt()).isNull();
        assertThat(restoredComment.getDeletedBy()).isNull();

        Like restoredLike = likeJpaRepository.findById(cascadeLikeId).orElseThrow();
        assertThat(restoredLike.getDeletedAt()).isNull();
        assertThat(restoredLike.getDeletedBy()).isNull();
    }

    // ── 001-2 ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("직접 삭제된 댓글(deletedByPostDeletion=false)은 복구되지 않고 deletedAt 유지")
    void restorePost_직접삭제_댓글은_복구되지_않음() {
        postService.restorePost(savedPostId, authorId, "USER");

        Comment directComment = commentJpaRepository.findById(directlyDeletedCommentId).orElseThrow();
        assertThat(directComment.getDeletedAt()).isNotNull();
        assertThat(directComment.isDeletedByPostDeletion()).isFalse();
    }

    // ── 001-3 ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("복구된 댓글·좋아요의 deletedByPostDeletion이 false로 리셋")
    void restorePost_복구후_플래그_리셋() {
        postService.restorePost(savedPostId, authorId, "USER");

        Comment restoredComment = commentJpaRepository.findById(cascadeCommentId).orElseThrow();
        assertThat(restoredComment.isDeletedByPostDeletion()).isFalse();

        Like restoredLike = likeJpaRepository.findById(cascadeLikeId).orElseThrow();
        assertThat(restoredLike.isDeletedByPostDeletion()).isFalse();
    }

    // ── 001-4 ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("작성자·ADMIN이 아닌 USER의 복구 시도 → ForbiddenException")
    void restorePost_권한없는_사용자_복구시도() {
        UUID otherId = UUID.randomUUID();

        assertThatThrownBy(() -> postService.restorePost(savedPostId, otherId, "USER"))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── 001-5 ────────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("복구 성공 시 POST_RESTORED outbox 이벤트 1건 저장, payload에 postId·authorId 포함")
    void restorePost_POST_RESTORED_이벤트_저장() throws Exception {
        postService.restorePost(savedPostId, authorId, "USER");

        List<OutboxEvent> events = outboxEventJpaRepository.findAll();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo("POST_RESTORED");
        assertThat(event.getTopic()).isEqualTo("post-events");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode payload = mapper.readTree(event.getPayload());
        assertThat(payload.get("postId").asText()).isEqualTo(savedPostId.toString());
        assertThat(payload.get("authorId").asText()).isEqualTo(authorId.toString());
    }
}