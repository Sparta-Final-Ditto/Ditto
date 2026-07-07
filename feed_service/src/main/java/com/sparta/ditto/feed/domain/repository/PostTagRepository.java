package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.PostTag;
import java.util.List;
import java.util.UUID;

public interface PostTagRepository {

    List<PostTag> findByPostId(UUID postId);

    void deleteByPostId(UUID postId);
}
