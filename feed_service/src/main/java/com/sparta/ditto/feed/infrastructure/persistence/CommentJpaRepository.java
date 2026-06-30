package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.Comment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentJpaRepository extends JpaRepository<Comment, UUID> {

    Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.deletedAt = :now, c.deletedBy = :deletedBy,"
            + " c.deletedByPostDeletion = true"
            + " WHERE c.postId = :postId AND c.deletedAt IS NULL")
    int softDeleteAllByPostId(
            @Param("postId") UUID postId,
            @Param("deletedBy") UUID deletedBy,
            @Param("now") Instant now);

    List<Comment> findByPostIdAndDeletedAtIsNull(UUID postId);

    @Query(value = """
            SELECT * FROM comments
            WHERE post_id = CAST(:postId AS uuid)
              AND deleted_at IS NULL
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR created_at > CAST(:cursorAt AS timestamptz)
                OR (created_at = CAST(:cursorAt AS timestamptz)
                    AND id > CAST(:cursorId AS uuid))
              )
            ORDER BY created_at ASC, id ASC
            """, nativeQuery = true)
    List<Comment> findByPostIdWithCursor(
            @Param("postId") UUID postId,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.postId = :postId")
    void hardDeleteAllByPostId(@Param("postId") UUID postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.deletedAt = null, c.deletedBy = null, c.deletedByPostDeletion = false"
            + " WHERE c.postId = :postId AND c.deletedByPostDeletion = true")
    int restoreAllByPostId(@Param("postId") UUID postId);
}
