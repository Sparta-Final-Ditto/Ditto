package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.exception.ForbiddenException;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteCommentServiceTest {

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
    private final UUID postOwnerId = UUID.randomUUID();
    private final UUID commentOwnerId = UUID.randomUUID();

    private Post createPost(UUID userId) {
        Post post = new Post(userId, "닉네임", "내용", "서울 성동구",
                37.5563, 127.0374, LocationScope.PUBLIC, true);
        ReflectionTestUtils.setField(post, "id", postId);
        return post;
    }

    private Comment createComment(UUID commentId, UUID authorId) {
        Comment comment = new Comment(postId, authorId, "댓글 내용");
        ReflectionTestUtils.setField(comment, "id", commentId);
        return comment;
    }

    // -------------------------------------------------------
    // 게시글 작성자가 타인의 댓글 삭제 → 예외 권한 허용 검증
    // -------------------------------------------------------
    @Test
    @DisplayName("게시글 작성자가 타인의 댓글을 삭제 → 예외적 권한 허용, 성공")
    void deleteComment_게시글작성자_타인댓글삭제_성공() {
        // given
        UUID commentId = UUID.randomUUID();
        Post post = createPost(postOwnerId);
        Comment comment = createComment(commentId, commentOwnerId); // 댓글 작성자 != 게시글 작성자

        when(commentRepository.findByIdAndDeletedAtIsNull(commentId)).thenReturn(Optional.of(comment));
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));

        // when - postOwnerId (게시글 작성자)가 타인의 댓글 삭제 시도
        assertThatCode(() ->
                postInteractionService.deleteComment(postOwnerId, "USER", postId, commentId))
                .doesNotThrowAnyException();

        // then
        verify(commentRepository).save(any(Comment.class));
        verify(postRepository).decrementCommentCount(postId);
    }

    // -------------------------------------------------------
    // ADMIN이 타인의 댓글 삭제 → 권한 검증
    // -------------------------------------------------------
    @Test
    @DisplayName("ADMIN이 타인의 댓글을 삭제 → ADMIN 권한으로 성공")
    void deleteComment_ADMIN_타인댓글삭제_성공() {
        // given
        UUID adminId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Post post = createPost(postOwnerId);
        Comment comment = createComment(commentId, commentOwnerId); // 댓글 작성자 != ADMIN

        when(commentRepository.findByIdAndDeletedAtIsNull(commentId)).thenReturn(Optional.of(comment));
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));

        // when - adminId (ADMIN role)가 타인의 댓글 삭제
        assertThatCode(() ->
                postInteractionService.deleteComment(adminId, "ADMIN", postId, commentId))
                .doesNotThrowAnyException();

        // then
        verify(commentRepository).save(any(Comment.class));
        verify(postRepository).decrementCommentCount(postId);
    }

    // -------------------------------------------------------
    // 댓글 삭제 성공 시 Outbox 이벤트 저장 없음
    // -------------------------------------------------------
    @Test
    @DisplayName("댓글 삭제 성공 시 Outbox(Kafka) 이벤트가 발행되지 않음")
    void deleteComment_정상삭제_Outbox_미발행() {
        // given
        UUID commentId = UUID.randomUUID();
        Post post = createPost(postOwnerId);
        Comment comment = createComment(commentId, commentOwnerId);

        when(commentRepository.findByIdAndDeletedAtIsNull(commentId)).thenReturn(Optional.of(comment));
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArgument(0));

        // when - 댓글 작성자 본인이 삭제
        postInteractionService.deleteComment(commentOwnerId, "USER", postId, commentId);

        // then - Outbox 이벤트가 저장되어서는 안 됨
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
        verify(outboxEventPort, never()).buildPostCommented(any(), any(), any());
    }

    // -------------------------------------------------------
    // 권한 없는 일반 USER가 타인 댓글 삭제 → ForbiddenException
    // -------------------------------------------------------
    @Test
    @DisplayName("일반 USER가 타인의 댓글 삭제 → ForbiddenException")
    void deleteComment_권한없는USER_ForbiddenException() {
        // given
        UUID strangerUserId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Post post = createPost(postOwnerId);
        Comment comment = createComment(commentId, commentOwnerId); // 댓글 작성자 != stranger

        when(commentRepository.findByIdAndDeletedAtIsNull(commentId)).thenReturn(Optional.of(comment));
        when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.of(post));

        // when & then - 댓글 작성자도, 게시글 작성자도, ADMIN도 아닌 유저 → 403
        assertThatThrownBy(() ->
                postInteractionService.deleteComment(strangerUserId, "USER", postId, commentId))
                .isInstanceOf(ForbiddenException.class);

        verify(commentRepository, never()).save(any());
        verify(postRepository, never()).decrementCommentCount(any());
    }
}