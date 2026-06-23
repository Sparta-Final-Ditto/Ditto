package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.repository.PostMediaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostMediaRepositoryImpl implements PostMediaRepository {

    private final PostMediaJpaRepository jpaRepository;

    @Override
    public List<PostMedia> findByPostIdOrderBySortOrder(UUID postId) {
        return jpaRepository.findByPostIdOrderBySortOrder(postId);
    }

    @Override
    public void deleteByPostId(UUID postId) {
        jpaRepository.deleteByPostId(postId);
    }
}
