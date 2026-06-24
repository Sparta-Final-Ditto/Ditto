package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.Comment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentJpaRepository extends JpaRepository<Comment, UUID> {

    Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);

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
}
