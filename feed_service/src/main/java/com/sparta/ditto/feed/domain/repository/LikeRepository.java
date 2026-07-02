package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Like;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LikeRepository {

    Like save(Like like);

    Optional<Like> findById(UUID id);

    void delete(Like like);

    int softDeleteAllByPostId(UUID postId, UUID deletedBy);

    Optional<Like> findByPostIdAndUserId(UUID postId, UUID userId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    List<UUID> findPostIdsByUserIdAndPostIdIn(UUID userId, List<UUID> postIds);

    List<Like> findLikesWithCursor(UUID postId, Instant cursorAt, UUID cursorId, int limit);

    void hardDeleteAllByPostId(UUID postId);

    void restoreAllByPostId(UUID postId);
}
