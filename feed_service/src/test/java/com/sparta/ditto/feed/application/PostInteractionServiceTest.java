package com.sparta.ditto.feed.application;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.feed.application.dto.response.LikeResponse;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
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
    private OutboxEventRepository outboxEventRepository;

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
    @DisplayName("TC-007-1: 유효한 postId → isLiked=true, likeCount 1 증가")
    void addLike_정상요청_isLiked_true_likeCount_증가() {
        // given
        Post post = createPost(ownerId, 5);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPostIdAndUserId(postId, likerId)).thenReturn(false);
        when(likeRepository.save(any(Like.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        // when
        LikeResponse result = postInteractionService.addLike(likerId, postId);

        // then
        assertThat(result.isLiked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(6);
        verify(likeRepository).save(any(Like.class));
        verify(postRepository).incrementLikeCount(postId);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("TC-007-3: 이미 좋아요한 게시글 → 409, DUPLICATE_LIKE")
    void addLike_중복좋아요_DuplicateLikeException() {
        // given
        Post post = createPost(ownerId, 5);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPostIdAndUserId(postId, likerId)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> postInteractionService.addLike(likerId, postId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode())
                        .isEqualTo("DUPLICATE_LIKE"));
        verify(likeRepository, never()).save(any());
        verify(postRepository, never()).incrementLikeCount(any());
    }

    @Test
    @DisplayName("TC-007-7: 본인 게시글 좋아요 → 좋아요 적용, Outbox 이벤트 저장 없음")
    void addLike_본인게시글_좋아요적용_outbox저장안함() {
        // given - likerId == ownerId (본인 게시글)
        Post post = createPost(likerId, 3);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPostIdAndUserId(postId, likerId)).thenReturn(false);
        when(likeRepository.save(any(Like.class))).thenAnswer(i -> i.getArgument(0));

        // when
        LikeResponse result = postInteractionService.addLike(likerId, postId);

        // then
        assertThat(result.isLiked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(4);
        verify(postRepository).incrementLikeCount(postId);
        verify(outboxEventRepository, never()).save(any());
    }
}