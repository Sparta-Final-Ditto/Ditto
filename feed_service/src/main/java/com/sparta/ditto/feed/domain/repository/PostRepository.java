package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.Visibility;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository {

    Post save(Post post);

    Optional<Post> findById(UUID id);

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    void incrementLikeCount(UUID postId);

    void decrementLikeCount(UUID postId);

    void incrementCommentCount(UUID postId);

    void decrementCommentCount(UUID postId);

    List<Post> findFeedWithCursor(Instant cursorAt, UUID cursorId, int limit);

    List<Post> findFeedByVisibilityWithCursor(
            List<Visibility> scopes, Instant cursorAt, UUID cursorId, int limit);

    /**
     * 랜덤 피드 조회 시 작성자 기준으로 특정 사용자(내가 차단한 사용자)를 제외한다.
     * excludeUserIds가 null/빈 목록이면 필터 없는 기존 조회와 동일하게 동작한다(구현체에서 분기).
     */
    List<Post> findFeedByVisibilityExcludingAuthorsWithCursor(
            List<Visibility> scopes, List<UUID> excludeUserIds,
            Instant cursorAt, UUID cursorId, int limit);

    List<Post> findByUserIdWithCursor(UUID userId, Instant cursorAt, UUID cursorId, int limit);

    List<Post> findFeedByUserIdsAndVisibilityWithCursor(
            List<UUID> userIds, List<Visibility> scopes,
            Instant cursorAt, UUID cursorId, int limit);

    List<Post> findByUserIdAndScopesWithCursor(
            UUID userId, List<Visibility> allowedScopes,
            Instant cursorAt, UUID cursorId, int limit);

    List<Post> findExpiredSoftDeleted(Instant cutoff, int limit);

    void hardDeleteById(UUID postId);

    void restoreById(UUID postId);
}
