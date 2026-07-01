package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
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
    private final OutboxEventPort outboxEventPort;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void purgePost(UUID postId) {
        // outbox 이벤트 생성을 위해 물리 삭제 전에 post를 먼저 확보한다.
        Post post = postRepository.findById(postId)
                .orElseThrow(PostNotFoundException::new);

        // deletedBy: hard delete는 시스템(스케줄러)이 수행. 별도 시스템 UUID 상수 미정의이므로
        // soft delete 요청자(post.getDeletedBy())를 재사용한다.
        OutboxEvent outboxEvent = outboxEventPort.buildPostHardDeleted(post, post.getDeletedBy());
        outboxEventRepository.save(outboxEvent);

        commentRepository.hardDeleteAllByPostId(postId);
        likeRepository.hardDeleteAllByPostId(postId);
        postMediaRepository.deleteByPostId(postId);
        postTagRepository.deleteByPostId(postId);
        postRepository.hardDeleteById(postId);
    }
}