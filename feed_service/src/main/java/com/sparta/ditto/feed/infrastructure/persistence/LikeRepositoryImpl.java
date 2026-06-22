package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository jpaRepository;

    @Override
    public Like save(Like like) {
        return jpaRepository.save(like);
    }

    @Override
    public Optional<Like> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(Like like) {
        jpaRepository.delete(like);
    }

    @Override
    public Optional<Like> findByPostIdAndUserId(UUID postId, UUID userId) {
        return jpaRepository.findByPostIdAndUserId(postId, userId);
    }

    @Override
    public boolean existsByPostIdAndUserId(UUID postId, UUID userId) {
        return jpaRepository.existsByPostIdAndUserId(postId, userId);
    }

    @Override
    public List<UUID> findPostIdsByUserIdAndPostIdIn(UUID userId, List<UUID> postIds) {
        return jpaRepository.findPostIdsByUserIdAndPostIdIn(userId, postIds);
    }

    @Override
    public List<Like> findLikesWithCursor(UUID postId, Instant cursorAt, UUID cursorId, int limit) {
        return jpaRepository.findLikesWithCursor(postId, cursorAt, cursorId, PageRequest.of(0, limit));
    }
}
