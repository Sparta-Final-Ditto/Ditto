package com.sparta.ditto.feed.infrastructure.persistence;

import com.sparta.ditto.feed.domain.entity.PostTag;
import com.sparta.ditto.feed.domain.repository.PostTagRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostTagRepositoryImpl implements PostTagRepository {

    private final PostTagJpaRepository jpaRepository;

    @Override
    public List<PostTag> findByPostId(UUID postId) {
        return jpaRepository.findByPostId(postId);
    }

    @Override
    public void deleteByPostId(UUID postId) {
        jpaRepository.deleteByPostId(postId);
    }
}
