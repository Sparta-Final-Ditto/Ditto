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

    @Transactional
    public LikeResponse removeLike(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(PostNotFoundException::new);
        Like like = likeRepository.findByPostIdAndUserId(postId, userId)
                .orElseThrow(LikeNotFoundException::new);
        likeRepository.delete(like);
        postRepository.decrementLikeCount(postId);
        return LikeResponse.unliked(post);
    }

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
