package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.LocationScope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, UUID> {

    boolean existsByIdAndUserId(UUID id, UUID userId);

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
            WHERE location_scope IN (:#{#scopes.![name()]})
              AND deleted_at IS NULL
              AND (
                CAST(:cursorAt AS timestamptz) IS NULL
                OR created_at < CAST(:cursorAt AS timestamptz)
                OR (created_at = CAST(:cursorAt AS timestamptz) AND id < CAST(:cursorId AS uuid))
              )
            ORDER BY created_at DESC, id DESC
            """, nativeQuery = true)
    List<Post> findFeedByLocationScopeWithCursor(
            @Param("scopes") List<LocationScope> scopes,
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
}
