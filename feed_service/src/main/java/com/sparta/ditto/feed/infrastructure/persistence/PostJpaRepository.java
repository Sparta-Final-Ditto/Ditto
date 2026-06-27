package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.Visibility;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostJpaRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    void incrementLikeCount(@Param("postId") UUID postId);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = GREATEST(p.likeCount - 1, 0) WHERE p.id = :postId")
    void decrementLikeCount(@Param("postId") UUID postId);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
    void incrementCommentCount(@Param("postId") UUID postId);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = GREATEST(p.commentCount - 1, 0)"
            + " WHERE p.id = :postId")
    void decrementCommentCount(@Param("postId") UUID postId);

    @Query(value = """
            SELECT * FROM posts
            WHERE deleted_at IS NULL
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR created_at < CAST(:cursorAt AS timestamptz)
                OR (created_at = CAST(:cursorAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
              )
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<Post> findFeedWithCursor(
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Query(value = """
            SELECT * FROM posts
            WHERE visibility IN (:#{#scopes.![name()]})
              AND deleted_at IS NULL
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR created_at < CAST(:cursorAt AS timestamptz)
                OR (created_at = CAST(:cursorAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
              )
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<Post> findFeedByVisibilityWithCursor(
            @Param("scopes") List<Visibility> scopes,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Query(value = """
            SELECT * FROM posts
            WHERE user_id = CAST(:userId AS uuid)
              AND deleted_at IS NULL
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR created_at < CAST(:cursorAt AS timestamptz)
                OR (created_at = CAST(:cursorAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
              )
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<Post> findByUserIdWithCursor(
            @Param("userId") UUID userId,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Query(value = """
            SELECT * FROM posts
            WHERE CAST(user_id AS text) IN (:#{#userIds.![toString()]})
              AND visibility IN (:#{#scopes.![name()]})
              AND deleted_at IS NULL
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR created_at < CAST(:cursorAt AS timestamptz)
                OR (created_at = CAST(:cursorAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
              )
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<Post> findFeedByUserIdsAndVisibilityWithCursor(
            @Param("userIds") List<UUID> userIds,
            @Param("scopes") List<Visibility> scopes,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Query(value = """
            SELECT * FROM posts
            WHERE user_id = CAST(:userId AS uuid)
              AND visibility IN (:#{#scopes.![name()]})
              AND deleted_at IS NULL
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR created_at < CAST(:cursorAt AS timestamptz)
                OR (created_at = CAST(:cursorAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
              )
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<Post> findByUserIdAndScopesWithCursor(
            @Param("userId") UUID userId,
            @Param("scopes") List<Visibility> scopes,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}
