package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
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
    public Optional<Post> findByIdAndDeletedAtIsNull(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean existsByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.existsByIdAndUserId(id, userId);
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
    public List<Post> findFeedByVisibilityWithCursor(
            List<Visibility> scopes, Instant cursorAt, UUID cursorId, int limit) {
        return jpaRepository.findFeedByVisibilityWithCursor(
                scopes, cursorAt, cursorId, PageRequest.of(0, limit));
    }

    @Override
    public List<Post> findByUserIdWithCursor(
            UUID userId, Instant cursorAt, UUID cursorId, int limit) {
        return jpaRepository.findByUserIdWithCursor(
                userId, cursorAt, cursorId, PageRequest.of(0, limit));
    }

    @Override
    public List<Post> findFeedByUserIdsAndVisibilityWithCursor(
            List<UUID> userIds, List<Visibility> scopes,
            Instant cursorAt, UUID cursorId, int limit) {
        return jpaRepository.findFeedByUserIdsAndVisibilityWithCursor(
                userIds, scopes, cursorAt, cursorId, PageRequest.of(0, limit));
    }

    @Override
    public List<Post> findByUserIdAndScopesWithCursor(
            UUID userId, List<Visibility> allowedScopes,
            Instant cursorAt, UUID cursorId, int limit) {
        return jpaRepository.findByUserIdAndScopesWithCursor(
                userId, allowedScopes, cursorAt, cursorId, PageRequest.of(0, limit));
    }

    @Override
    public List<Post> findExpiredSoftDeleted(Instant cutoff, int limit) {
        return jpaRepository.findExpiredSoftDeleted(cutoff, PageRequest.of(0, limit));
    }

    @Override
    public void hardDeleteById(UUID postId) {
        jpaRepository.hardDeleteById(postId);
    }

    @Override
    public void restoreById(UUID postId) {
        jpaRepository.restoreById(postId);
    }
}
