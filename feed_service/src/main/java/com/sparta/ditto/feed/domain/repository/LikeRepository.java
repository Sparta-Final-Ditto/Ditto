package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Like;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like, UUID> {

    Optional<Like> findByPostIdAndUserId(UUID postId, UUID userId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);

    @Query("SELECT l.postId FROM Like l WHERE l.userId = :userId AND l.postId IN :postIds")
    List<UUID> findPostIdsByUserIdAndPostIdIn(@Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);
}
