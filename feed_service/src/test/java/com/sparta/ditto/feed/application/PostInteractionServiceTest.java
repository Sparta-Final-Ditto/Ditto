package com.sparta.ditto.feed.application;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.application.dto.result.CommentResult;
import com.sparta.ditto.feed.application.dto.command.CreateCommentCommand;
import com.sparta.ditto.feed.application.dto.result.LikeResult;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.application.UploadUrlResult.port.out.OutboxEventPort;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostInteractionServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventPort outboxEventPort;

    @InjectMocks
    private PostInteractionService postInteractionService;

    private final UUID postId = UUID.randomUUID();
    private final UUID likerId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private Post createPost(UUID userId, int likeCount) {
        Post post = new Post(userId, "새벽러너", "오늘 러닝 완료", "서울 성동구",
                37.5563, 127.0374, LocationScope.PUBLIC, true);
        ReflectionTestUtils.setField(post, "id", postId);
        ReflectionTestUtils.setField(post, "likeCount", likeCount);
        return post;
    }

    @Test
    @DisplayName("유효한 postId → isLiked=true, likeCount 1 증가")
    void addLike_정상요청_isLiked_true_likeCount_증가() {
        // given
        Post post = createPost(ownerId, 5);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPostIdAndUserId(postId, likerId)).thenReturn(false);
        when(likeRepository.save(any(Like.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxEventPort.buildPostLiked(any(Post.class), any(UUID.class)))
                .thenReturn(new OutboxEvent("post-events", "POST_LIKED", "{}"));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        // when
        LikeResult result = postInteractionService.addLike(likerId, postId, "테스트닉네임");

        // then
        assertThat(result.isLiked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(6);
        verify(likeRepository).save(any(Like.class));
        verify(postRepository).incrementLikeCount(postId);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("이미 좋아요한 게시글 → 409, DUPLICATE_LIKE")
    void addLike_중복좋아요_DuplicateLikeException() {
        // given
        Post post = createPost(ownerId, 5);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPostIdAndUserId(postId, likerId)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> postInteractionService.addLike(likerId, postId, "테스트닉네임"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode())
                        .isEqualTo("DUPLICATE_LIKE"));
        verify(likeRepository, never()).save(any());
        verify(postRepository, never()).incrementLikeCount(any());
    }

    @Test
    @DisplayName("본인 게시글 좋아요 → 좋아요 적용, Outbox 이벤트 저장 없음")
    void addLike_본인게시글_좋아요적용_outbox저장안함() {
        // given - likerId == ownerId (본인 게시글)
        Post post = createPost(likerId, 3);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPostIdAndUserId(postId, likerId)).thenReturn(false);
        when(likeRepository.save(any(Like.class))).thenAnswer(i -> i.getArgument(0));

        // when
        LikeResult result = postInteractionService.addLike(likerId, postId, "테스트닉네임");

        // then
        assertThat(result.isLiked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(4);
        verify(postRepository).incrementLikeCount(postId);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("좋아요 존재 → isLiked=false, likeCount 1 감소")
    void removeLike_정상취소_isLiked_false_likeCount_감소() {
        // given
        Post post = createPost(ownerId, 5);
        Like like = new Like(postId, likerId);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.findByPostIdAndUserId(postId, likerId)).thenReturn(Optional.of(like));

        // when
        LikeResult result = postInteractionService.removeLike(likerId, postId);

        // then
        assertThat(result.isLiked()).isFalse();
        assertThat(result.likeCount()).isEqualTo(4);
        verify(likeRepository).delete(like);
        verify(postRepository).decrementLikeCount(postId);
    }

    @Test
    @DisplayName("좋아요하지 않은 게시글 취소 → 404, LIKE_NOT_FOUND")
    void removeLike_좋아요없음_LikeNotFoundException() {
        // given
        Post post = createPost(ownerId, 5);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.findByPostIdAndUserId(postId, likerId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postInteractionService.removeLike(likerId, postId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode())
                        .isEqualTo("LIKE_NOT_FOUND"));
        verify(likeRepository, never()).delete(any());
        verify(postRepository, never()).decrementLikeCount(any());
    }

    @Test
    @DisplayName("likeCount=0일 때 취소 → likeCount 0으로 고정 (음수 방지)")
    void removeLike_likeCount_0일때_음수방지() {
        // given
        Post post = createPost(ownerId, 0);
        Like like = new Like(postId, likerId);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.findByPostIdAndUserId(postId, likerId)).thenReturn(Optional.of(like));

        // when
        LikeResult result = postInteractionService.removeLike(likerId, postId);

        // then
        assertThat(result.likeCount()).isEqualTo(0);
        assertThat(result.isLiked()).isFalse();
    }

    @Test
    @DisplayName("좋아요 취소 시 Outbox 이벤트 저장 없음")
    void removeLike_Outbox_저장_없음() {
        // given
        Post post = createPost(ownerId, 3);
        Like like = new Like(postId, likerId);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.findByPostIdAndUserId(postId, likerId)).thenReturn(Optional.of(like));

        // when
        postInteractionService.removeLike(likerId, postId);

        // then
        verify(outboxEventRepository, never()).save(any());
    }

    // ============================================================
    // POST /posts/{postId}/comments (댓글 등록)
    // ============================================================

    private Comment createSavedComment(UUID commentPostId, UUID userId, String content) {
        Comment comment = new Comment(commentPostId, userId, content);
        ReflectionTestUtils.setField(comment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(comment, "createdAt", Instant.now());
        return comment;
    }

    @Test
    @DisplayName("정상 요청 → commentId 반환, comments 저장 확인")
    void createComment_정상요청_commentId_반환() {
        // given
        UUID commenterId = UUID.randomUUID();
        Post post = createPost(ownerId, 0);
        Comment savedComment = createSavedComment(postId, commenterId, "댓글 내용");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(outboxEventPort.buildPostCommented(any(), any(), any()))
                .thenReturn(new OutboxEvent("post-events", "POST_COMMENTED", "{}"));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        // when
        CommentResult result = postInteractionService.createComment(
                commenterId, "닉네임", postId, new CreateCommentCommand("댓글 내용"));

        // then
        assertThat(result.commentId()).isNotNull();
        assertThat(result.postId()).isEqualTo(postId);
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    @DisplayName("없는 postId → 404, POST_NOT_FOUND")
    void createComment_없는postId_PostNotFoundException() {
        // given
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postInteractionService.createComment(
                UUID.randomUUID(), "닉네임", postId, new CreateCommentCommand("댓글")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode())
                        .isEqualTo("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 생성 응답 → isMyComment=true, canDelete=true, postId 일치")
    void createComment_응답필드_검증() {
        // given
        UUID commenterId = UUID.randomUUID();
        Post post = createPost(ownerId, 0);
        Comment savedComment = createSavedComment(postId, commenterId, "댓글 내용");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(outboxEventPort.buildPostCommented(any(), any(), any()))
                .thenReturn(new OutboxEvent("post-events", "POST_COMMENTED", "{}"));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        // when
        CommentResult result = postInteractionService.createComment(
                commenterId, "닉네임", postId, new CreateCommentCommand("댓글 내용"));

        // then
        assertThat(result.isMyComment()).isTrue();
        assertThat(result.isDeletable()).isTrue();
        assertThat(result.postId()).isEqualTo(postId);
    }

    @Test
    @DisplayName("타인 게시글 댓글 → POST_COMMENTED Outbox 이벤트 저장")
    void createComment_타인게시글_outbox_저장() {
        // given
        UUID commenterId = UUID.randomUUID();
        Post post = createPost(ownerId, 0);
        Comment savedComment = createSavedComment(postId, commenterId, "댓글");
        OutboxEvent outboxEvent = new OutboxEvent("post-events", "POST_COMMENTED", "{}");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(outboxEventPort.buildPostCommented(any(), any(), any())).thenReturn(outboxEvent);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        // when
        postInteractionService.createComment(commenterId, "닉네임", postId, new CreateCommentCommand("댓글"));

        // then
        verify(outboxEventPort).buildPostCommented(any(Post.class), any(Comment.class), any(UUID.class));
        verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    @DisplayName("본인 게시글 댓글 → Outbox 이벤트 생성 없음")
    void createComment_본인게시글_outbox_미생성() {
        // given
        Post post = createPost(ownerId, 0);
        Comment savedComment = createSavedComment(postId, ownerId, "댓글");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);

        // when
        postInteractionService.createComment(ownerId, "닉네임", postId, new CreateCommentCommand("댓글"));

        // then
        verify(outboxEventPort, never()).buildPostCommented(any(), any(), any());
        verify(outboxEventRepository, never()).save(any());
    }
}
