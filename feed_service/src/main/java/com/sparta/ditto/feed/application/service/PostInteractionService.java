package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.dto.response.LikeListResponse;
import com.sparta.ditto.feed.application.dto.response.LikeResponse;
import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.exception.DuplicateLikeException;
import com.sparta.ditto.feed.domain.exception.LikeNotFoundException;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.infrastructure.kafka.OutboxEventFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostInteractionService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * 게시글 좋아요를 추가한다.
     * 중복 좋아요 여부를 확인하고, 좋아요를 저장한 후 게시글의 좋아요 수를 증가시킨다.
     * 본인 게시글이 아닌 경우 Outbox 이벤트를 발행하여 알림을 전송한다.
     */
    @Transactional
    public LikeResponse addLike(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(PostNotFoundException::new);

        if (likeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new DuplicateLikeException();
        }

        likeRepository.save(new Like(postId, userId));
        postRepository.incrementLikeCount(postId);

        if (!userId.equals(post.getUserId())) {
            outboxEventRepository.save(OutboxEventFactory.createPostLiked(post, userId));
        }

        return LikeResponse.liked(post);
    }

    /**
     * 게시글 좋아요를 취소한다.
     * 좋아요 존재 여부를 확인한 후 삭제하고, 게시글의 좋아요 수를 감소시킨다.
     */
    @Transactional
    public LikeResponse removeLike(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(PostNotFoundException::new);
        Like like = likeRepository.findByPostIdAndUserId(postId, userId)
                .orElseThrow(LikeNotFoundException::new);
        likeRepository.delete(like);
        postRepository.decrementLikeCount(postId);
        return LikeResponse.unliked(post);
    }

    /**
     * 게시글에 좋아요를 누른 사용자 목록을 cursor 기반 페이지네이션으로 조회한다.
     * cursor가 주어지면 해당 좋아요 시점 이후의 데이터를 반환하며,
     * 다음 페이지 존재 여부와 nextCursor를 함께 반환한다.
     */
    @Transactional(readOnly = true)
    public LikeListResponse getLikes(UUID postId, UUID cursor, int size) {
        Post post = postRepository.findById(postId).orElseThrow(PostNotFoundException::new);

        Instant cursorAt = null;
        UUID cursorId = null;
        if (cursor != null) {
            Like cursorLike = likeRepository.findById(cursor).orElse(null);
            if (cursorLike != null) {
                cursorAt = cursorLike.getCreatedAt();
                cursorId = cursor;
            }
        }

        List<Like> fetched = likeRepository.findLikesWithCursor(
                postId, cursorAt, cursorId, PageRequest.of(0, size + 1)
        );

        boolean hasNext = fetched.size() > size;
        List<Like> pageResult = hasNext ? fetched.subList(0, size) : fetched;
        UUID nextCursor = hasNext ? pageResult.get(pageResult.size() - 1).getId() : null;

        return LikeListResponse.of(pageResult, post.getLikeCount(), nextCursor, hasNext);
    }
}
