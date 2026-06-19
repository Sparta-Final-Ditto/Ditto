package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.LocationScope;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, UUID> {

    boolean existsByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT p FROM Post p
            WHERE (:cursorAt IS NULL AND :cursorId IS NULL)
               OR (p.createdAt < :cursorAt)
               OR (p.createdAt = :cursorAt AND p.id < :cursorId)
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Post> findFeedWithCursor(
            @Param("cursorAt") LocalDateTime cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM Post p
            WHERE p.locationScope IN :scopes
              AND ((:cursorAt IS NULL AND :cursorId IS NULL)
               OR (p.createdAt < :cursorAt)
               OR (p.createdAt = :cursorAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Post> findFeedByLocationScopeWithCursor(
            @Param("scopes") List<LocationScope> scopes,
            @Param("cursorAt") LocalDateTime cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );

    @Query("""
            SELECT p FROM Post p
            WHERE p.userId = :userId
              AND ((:cursorAt IS NULL AND :cursorId IS NULL)
               OR (p.createdAt < :cursorAt)
               OR (p.createdAt = :cursorAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<Post> findByUserIdWithCursor(
            @Param("userId") UUID userId,
            @Param("cursorAt") LocalDateTime cursorAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}
