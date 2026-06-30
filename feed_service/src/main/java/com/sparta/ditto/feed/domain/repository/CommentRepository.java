package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Comment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(UUID id);

    int softDeleteAllByPostId(UUID postId, UUID deletedBy);

    Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);

    List<Comment> findByPostIdWithCursor(UUID postId, Instant cursorAt, UUID cursorId, int limit);

    List<Comment> findByPostIdAndDeletedAtIsNull(UUID postId);

    void hardDeleteAllByPostId(UUID postId);
}
