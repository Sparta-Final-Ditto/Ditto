package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.PostMedia;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostMediaJpaRepository extends JpaRepository<PostMedia, UUID> {

    List<PostMedia> findByPostIdOrderBySortOrder(UUID postId);

    void deleteByPostId(UUID postId);
}
