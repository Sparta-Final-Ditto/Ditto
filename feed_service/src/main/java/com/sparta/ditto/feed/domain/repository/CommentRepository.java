package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Comment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            SELECT c FROM Comment c
            WHERE c.postId = :postId
              AND c.deletedAt IS NULL
              AND ((:cursorAt IS NULL AND :cursorId IS NULL)
               OR (c.createdAt > :cursorAt)
               OR (c.createdAt = :cursorAt AND c.id > :cursorId))
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<Comment> findByPostIdWithCursor(
            @Param("postId") UUID postId,
            @Param("cursorAt") Instant cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}
