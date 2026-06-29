package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.PostMediaRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.repository.PostTagRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 영구 삭제(hard delete) 서비스.
 *
 * <p>삭제 순서: comments → likes → post_media → post_tags → posts
 *
 * <p>post_media, post_tags 테이블은 posts(id)를 FK로 참조하며 ON DELETE NO ACTION이므로
 * posts 행 삭제 전에 반드시 먼저 제거해야 FK 위반을 피할 수 있다.
 * comments, likes는 post_id를 UUID 컬럼으로만 보유하고 FK 제약이 없으나,
 * 고아 데이터를 남기지 않기 위해 함께 삭제한다.
 */
@Service
@RequiredArgsConstructor
public class PostHardDeleteService {

    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostTagRepository postTagRepository;
    private final PostRepository postRepository;

    @Transactional
    public void purgePost(UUID postId) {
        commentRepository.hardDeleteAllByPostId(postId);
        likeRepository.hardDeleteAllByPostId(postId);
        postMediaRepository.deleteByPostId(postId);
        postTagRepository.deleteByPostId(postId);
        postRepository.hardDeleteById(postId);
    }
}
