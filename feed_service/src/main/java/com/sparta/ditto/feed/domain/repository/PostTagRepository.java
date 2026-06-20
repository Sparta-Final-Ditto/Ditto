package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.PostTag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 게시글 태그 JPA 레포지토리 */
public interface PostTagRepository extends JpaRepository<PostTag, UUID> {

    List<PostTag> findByPostId(UUID postId);

    void deleteByPostId(UUID postId);
}
