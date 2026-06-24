package com.sparta.ditto.feed.application;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.application.dto.command.CreateCommentCommand;
import com.sparta.ditto.feed.application.dto.query.GetCommentsQuery;
import com.sparta.ditto.feed.application.dto.query.GetLikesQuery;
import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * soft delete된 게시글에 접근하는 모든 서비스 경로가 POST_NOT_FOUND(404)를 반환하는지 검증.
 * findByIdAndDeletedAtIsNull은 stub 없음 → Mockito 기본값 Optional.empty() → PostNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
class PostDeletedAccess404Test {

    @Mock private PostRepository postRepository;
    @Mock private LikeRepository likeRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private OutboxEventPort outboxEventPort;

    @InjectMocks private PostInteractionService postInteractionService;
    @InjectMocks private PostService postService;

    private final UUID postId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(postService, "cloudfrontDomain", "https://cdn.example.com");
    }

    private void assertPostNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode())
                        .isEqualTo("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("삭제된 게시글 단일 조회 → 404 POST_NOT_FOUND")
    void getPostDetail_삭제된게시글_PostNotFoundException() {
        assertPostNotFound(() -> postService.getPostDetail(postId, userId));
    }

    @Test
    @DisplayName("삭제된 게시글 좋아요 생성 → 404 POST_NOT_FOUND")
    void addLike_삭제된게시글_PostNotFoundException() {
        assertPostNotFound(() -> postInteractionService.addLike(userId, postId, "닉네임"));
    }

    @Test
    @DisplayName("삭제된 게시글 좋아요 취소 → 404 POST_NOT_FOUND")
    void removeLike_삭제된게시글_PostNotFoundException() {
        assertPostNotFound(() -> postInteractionService.removeLike(userId, postId));
    }

    @Test
    @DisplayName("삭제된 게시글 좋아요 목록 조회 → 404 POST_NOT_FOUND")
    void getLikes_삭제된게시글_PostNotFoundException() {
        assertPostNotFound(() ->
                postInteractionService.getLikes(new GetLikesQuery(postId, null, 20)));
    }

    @Test
    @DisplayName("삭제된 게시글 댓글 생성 → 404 POST_NOT_FOUND")
    void createComment_삭제된게시글_PostNotFoundException() {
        assertPostNotFound(() ->
                postInteractionService.createComment(
                        userId, "닉네임", postId, new CreateCommentCommand("댓글")));
    }

    @Test
    @DisplayName("삭제된 게시글 댓글 목록 조회 → 404 POST_NOT_FOUND")
    void getComments_삭제된게시글_PostNotFoundException() {
        assertPostNotFound(() ->
                postInteractionService.getComments(
                        new GetCommentsQuery(postId, userId, "USER", null, 20)));
    }

    @Test
    @DisplayName("삭제된 게시글의 댓글 삭제 → 404 POST_NOT_FOUND")
    void deleteComment_삭제된게시글_PostNotFoundException() {
        UUID commentId = UUID.randomUUID();
        Comment comment = new Comment(postId, userId, "닉네임", "댓글");
        ReflectionTestUtils.setField(comment, "id", commentId);
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));

        assertPostNotFound(() ->
                postInteractionService.deleteComment(userId, "USER", postId, commentId));
    }
}