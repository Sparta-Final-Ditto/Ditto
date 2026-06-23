package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final PostJpaRepository jpaRepository;

    @Override
    public Post save(Post post) {
        return jpaRepository.save(post);
    }

    @Override
    public Optional<Post> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.existsByIdAndUserId(id, userId);
    }

    @Override
    public void incrementViewCount(UUID postId) {
        jpaRepository.incrementViewCount(postId);
    }

    @Override
    public void incrementLikeCount(UUID postId) {
        jpaRepository.incrementLikeCount(postId);
    }

    @Override
    public void decrementLikeCount(UUID postId) {
        jpaRepository.decrementLikeCount(postId);
    }

    @Override
    public void incrementCommentCount(UUID postId) {
        jpaRepository.incrementCommentCount(postId);
    }

    @Override
    public void decrementCommentCount(UUID postId) {
        jpaRepository.decrementCommentCount(postId);
    }

    @Override
    public List<Post> findFeedWithCursor(Instant cursorAt, UUID cursorId, int limit) {
        return jpaRepository.findFeedWithCursor(cursorAt, cursorId, PageRequest.of(0, limit));
    }

    @Override
    public List<Post> findFeedByLocationScopeWithCursor(
            List<LocationScope> scopes, Instant cursorAt, UUID cursorId, int limit) {
        return jpaRepository.findFeedByLocationScopeWithCursor(
                scopes, cursorAt, cursorId, PageRequest.of(0, limit));
    }

    @Override
    public List<Post> findByUserIdWithCursor(
            UUID userId, Instant cursorAt, UUID cursorId, int limit) {
        return jpaRepository.findByUserIdWithCursor(
                userId, cursorAt, cursorId, PageRequest.of(0, limit));
    }
}
