package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.application.dto.CommentListResult;
import com.sparta.ditto.feed.application.dto.GetCommentsQuery;
import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Post;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCommentsServiceTest {

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

    private Post createPost(UUID ownerId) {
        Post post = new Post(ownerId, "게시글작성자", "게시글 내용", "서울 성동구",
                37.5563, 127.0374, LocationScope.PUBLIC, true);
        ReflectionTestUtils.setField(post, "id", postId);
        return post;
    }

    private Comment createComment(UUID userId) {
        Comment comment = new Comment(postId, userId, "댓글 내용");
        ReflectionTestUtils.setField(comment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(comment, "createdAt", Instant.now());
        return comment;
    }

    @Test
    @DisplayName("내가 작성한 댓글 → isMyComment = true")
    void getComments_내댓글_isMyComment_true() {
        // given
        UUID requesterId = UUID.randomUUID();
        Post post = createPost(postOwnerId);
        Comment myComment = createComment(requesterId);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdWithCursor(eq(postId), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(myComment));

        // when
        CommentListResult result = postInteractionService.getComments(
                new GetCommentsQuery(postId, requesterId, "USER", null, 20));

        // then
        assertThat(result.comments()).hasSize(1);
        assertThat(result.comments().get(0).isMyComment()).isTrue();
    }

    @Test
    @DisplayName("타인이 작성한 댓글 → isMyComment = false")
    void getComments_타인댓글_isMyComment_false() {
        // given
        UUID requesterId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Post post = createPost(postOwnerId);
        Comment otherComment = createComment(otherUserId);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdWithCursor(eq(postId), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(otherComment));

        // when
        CommentListResult result = postInteractionService.getComments(
                new GetCommentsQuery(postId, requesterId, "USER", null, 20));

        // then
        assertThat(result.comments().get(0).isMyComment()).isFalse();
    }

    @Test
    @DisplayName("댓글 작성자 본인 → canDelete = true")
    void getComments_댓글작성자_canDelete_true() {
        // given
        UUID requesterId = UUID.randomUUID();
        Post post = createPost(postOwnerId);
        Comment myComment = createComment(requesterId);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdWithCursor(eq(postId), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(myComment));

        // when
        CommentListResult result = postInteractionService.getComments(
                new GetCommentsQuery(postId, requesterId, "USER", null, 20));

        // then
        assertThat(result.comments().get(0).isDeletable()).isTrue();
    }

    @Test
    @DisplayName("게시글 작성자 → canDelete = true")
    void getComments_게시글작성자_canDelete_true() {
        // given
        UUID commentAuthorId = UUID.randomUUID();
        Post post = createPost(postOwnerId);
        Comment otherComment = createComment(commentAuthorId);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdWithCursor(eq(postId), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(otherComment));

        // when - requesterId == postOwnerId (게시글 작성자)
        CommentListResult result = postInteractionService.getComments(
                new GetCommentsQuery(postId, postOwnerId, "USER", null, 20));

        // then
        assertThat(result.comments().get(0).isDeletable()).isTrue();
    }

    @Test
    @DisplayName("타인 (댓글 작성자도, 게시글 작성자도 아닌 USER) → canDelete = false")
    void getComments_타인_canDelete_false() {
        // given
        UUID requesterId = UUID.randomUUID();
        UUID commentAuthorId = UUID.randomUUID();
        Post post = createPost(postOwnerId);
        Comment otherComment = createComment(commentAuthorId);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdWithCursor(eq(postId), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(otherComment));

        // when
        CommentListResult result = postInteractionService.getComments(
                new GetCommentsQuery(postId, requesterId, "USER", null, 20));

        // then
        assertThat(result.comments().get(0).isDeletable()).isFalse();
    }

    @Test
    @DisplayName("(ADMIN): ADMIN 권한 보유자 → canDelete = true")
    void getComments_ADMIN권한_canDelete_true() {
        // given
        UUID requesterId = UUID.randomUUID();
        UUID commentAuthorId = UUID.randomUUID();
        Post post = createPost(postOwnerId);
        Comment otherComment = createComment(commentAuthorId);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdWithCursor(eq(postId), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(otherComment));

        // when - 댓글/게시글 작성자가 아니어도 ADMIN이면 canDelete = true
        CommentListResult result = postInteractionService.getComments(
                new GetCommentsQuery(postId, requesterId, "ADMIN", null, 20));

        // then
        assertThat(result.comments().get(0).isDeletable()).isTrue();
    }
}
