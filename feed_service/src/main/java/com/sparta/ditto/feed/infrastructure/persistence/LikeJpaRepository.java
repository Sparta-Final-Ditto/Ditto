package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.Like;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeJpaRepository extends JpaRepository<Like, UUID> {

    Optional<Like> findByPostIdAndUserId(UUID postId, UUID userId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    @Query("SELECT l.postId FROM Like l WHERE l.userId = :userId AND l.postId IN :postIds")
    List<UUID> findPostIdsByUserIdAndPostIdIn(
            @Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);

    @Query(value = """
            SELECT * FROM likes
            WHERE post_id = CAST(:postId AS uuid)
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR created_at < CAST(:cursorAt AS timestamptz)
                OR (created_at = CAST(:cursorAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
              )
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<Like> findLikesWithCursor(
            @Param("postId") UUID postId,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}
