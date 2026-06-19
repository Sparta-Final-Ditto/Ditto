package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.PostMedia;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostMediaRepository extends JpaRepository<PostMedia, UUID> {

    List<PostMedia> findByPostIdOrderBySortOrder(UUID postId);

    void deleteByPostId(UUID postId);
}
