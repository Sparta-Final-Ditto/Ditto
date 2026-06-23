package com.sparta.ditto.user.infrastructure.repository;

import com.sparta.ditto.user.domain.follow.Follow;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);
}
