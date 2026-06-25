package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepository {

    private final CommentJpaRepository jpaRepository;

    @Override
    public Comment save(Comment comment) {
        return jpaRepository.save(comment);
    }

    @Override
    public Optional<Comment> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Comment> findByIdAndDeletedAtIsNull(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Comment> findByPostIdWithCursor(
            UUID postId, Instant cursorAt, UUID cursorId, int limit) {
        return jpaRepository.findByPostIdWithCursor(
                postId, cursorAt, cursorId, PageRequest.of(0, limit));
    }

    @Override
    public List<Comment> findByPostIdAndDeletedAtIsNull(UUID postId) {
        return jpaRepository.findByPostIdAndDeletedAtIsNull(postId);
    }

    @Override
    @Transactional
    public int softDeleteAllByPostId(UUID postId, UUID deletedBy) {
        return jpaRepository.softDeleteAllByPostId(postId, deletedBy, Instant.now());
    }
}
