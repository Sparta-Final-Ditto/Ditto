package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.dto.CommentResult;
import com.sparta.ditto.feed.application.dto.CreateCommentCommand;
import com.sparta.ditto.feed.application.dto.GetLikesQuery;
import com.sparta.ditto.feed.application.dto.LikeListResult;
import com.sparta.ditto.feed.application.dto.LikeResult;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.exception.DuplicateLikeException;
import com.sparta.ditto.feed.domain.exception.LikeNotFoundException;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostInteractionService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPort outboxEventPort;

    // -------------------------------------------------------
    // 좋아요 추가
    // -------------------------------------------------------
    @Transactional
    public LikeResult addLike(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(PostNotFoundException::new);

        if (likeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new DuplicateLikeException();
        }

        likeRepository.save(new Like(postId, userId));
        postRepository.incrementLikeCount(postId);

        if (!userId.equals(post.getUserId())) {
            outboxEventRepository.save(outboxEventPort.buildPostLiked(post, userId));
        }

        return LikeResult.liked(post);
    }

    // -------------------------------------------------------
    // 좋아요 취소
    // -------------------------------------------------------
    @Transactional
    public LikeResult removeLike(UUID userId, UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(PostNotFoundException::new);
        Like like = likeRepository.findByPostIdAndUserId(postId, userId)
                .orElseThrow(LikeNotFoundException::new);
        likeRepository.delete(like);
        postRepository.decrementLikeCount(postId);
        return LikeResult.unliked(post);
    }

    // -------------------------------------------------------
    // 좋아요 목록 조회
    // -------------------------------------------------------
    @Transactional(readOnly = true)
    public LikeListResult getLikes(GetLikesQuery query) {
        Post post = postRepository.findById(query.postId()).orElseThrow(PostNotFoundException::new);

        Instant cursorAt = null;
        UUID cursorId = null;
        if (query.cursor() != null) {
            Like cursorLike = likeRepository.findById(query.cursor()).orElse(null);
            if (cursorLike != null) {
                cursorAt = cursorLike.getCreatedAt();
                cursorId = query.cursor();
            }
        }

        List<Like> fetched = likeRepository.findLikesWithCursor(
                query.postId(), cursorAt, cursorId, query.size() + 1
        );

        boolean hasNext = fetched.size() > query.size();
        List<Like> pageResult = hasNext ? fetched.subList(0, query.size()) : fetched;
        UUID nextCursor = hasNext ? pageResult.get(pageResult.size() - 1).getId() : null;

        return LikeListResult.of(pageResult, post.getLikeCount(), nextCursor, hasNext);
    }

    // -------------------------------------------------------
    // 댓글 생성
    // -------------------------------------------------------
    @Transactional
    public CommentResult createComment(CreateCommentCommand command) {
        Post post = postRepository.findById(command.postId()).orElseThrow(PostNotFoundException::new);
        Comment comment = commentRepository.save(new Comment(command.postId(), command.userId(), command.content()));
        postRepository.incrementCommentCount(command.postId());
        if (!command.userId().equals(post.getUserId())) {
            outboxEventRepository.save(outboxEventPort.buildPostCommented(post, comment, command.userId()));
        }
        return CommentResult.fromCreation(comment, command.userNickname());
    }
}
