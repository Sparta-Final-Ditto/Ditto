package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.dto.response.LikeResponse;
import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.exception.DuplicateLikeException;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.infrastructure.kafka.OutboxEventFactory;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
}