package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.PostMedia;
import java.util.List;
import java.util.UUID;

public interface PostMediaRepository {

    List<PostMedia> findByPostIdOrderBySortOrder(UUID postId);

    void deleteByPostId(UUID postId);
}
