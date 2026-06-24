package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.dto.command.CreateCommentCommand;
import com.sparta.ditto.feed.application.dto.query.GetCommentsQuery;
import com.sparta.ditto.feed.application.dto.query.GetLikesQuery;
import com.sparta.ditto.feed.application.dto.result.CommentListResult;
import com.sparta.ditto.feed.application.dto.result.CommentResult;
import com.sparta.ditto.feed.application.dto.result.LikeListResult;
import com.sparta.ditto.feed.application.dto.result.LikeResult;
import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.exception.CommentNotFoundException;
import com.sparta.ditto.feed.domain.exception.DuplicateLikeException;
import com.sparta.ditto.feed.domain.exception.ForbiddenException;
import com.sparta.ditto.feed.domain.exception.LikeNotFoundException;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
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
    public LikeResult addLike(UUID userId, UUID postId, String userNickname) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);

        if (likeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new DuplicateLikeException();
        }

        likeRepository.save(new Like(postId, userId, userNickname));
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
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);
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
        Post post = postRepository.findByIdAndDeletedAtIsNull(query.postId())
                .orElseThrow(PostNotFoundException::new);

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
    // 댓글 삭제
    // -------------------------------------------------------
    @Transactional
    public void deleteComment(UUID requesterId, String requesterRole, UUID postId, UUID commentId) {
        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(CommentNotFoundException::new);
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);

        boolean isCommentAuthor = requesterId.equals(comment.getUserId());
        boolean isPostAuthor = requesterId.equals(post.getUserId());
        boolean isAdmin = "ADMIN".equals(requesterRole);

        if (!isCommentAuthor && !isPostAuthor && !isAdmin) {
            throw new ForbiddenException();
        }

        comment.delete(requesterId);
        commentRepository.save(comment);
        postRepository.decrementCommentCount(postId);
    }

    // -------------------------------------------------------
    // 댓글 목록 조회
    // -------------------------------------------------------
    @Transactional(readOnly = true)
    public CommentListResult getComments(GetCommentsQuery query) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(query.postId())
                .orElseThrow(PostNotFoundException::new);

        Instant cursorAt = null;
        UUID cursorId = null;
        if (query.cursor() != null) {
            Comment cursorComment = commentRepository.findById(query.cursor()).orElse(null);
            if (cursorComment != null) {
                cursorAt = cursorComment.getCreatedAt();
                cursorId = query.cursor();
            }
        }

        List<Comment> fetched = commentRepository.findByPostIdWithCursor(
                query.postId(), cursorAt, cursorId, query.size() + 1);

        boolean hasNext = fetched.size() > query.size();
        List<Comment> pageResult = hasNext ? fetched.subList(0, query.size()) : fetched;
        UUID nextCursor = hasNext ? pageResult.get(pageResult.size() - 1).getId() : null;

        List<CommentResult> comments = pageResult.stream()
                .map(c -> CommentResult.fromList(
                        c, query.requesterId(), post.getUserId(), query.requesterRole()))
                .toList();

        return new CommentListResult(comments, nextCursor, hasNext);
    }

    // -------------------------------------------------------
    // 댓글 생성
    // -------------------------------------------------------
    @Transactional
    public CommentResult createComment(
            UUID userId, String nickname, UUID postId, CreateCommentCommand command) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);
        Comment comment = commentRepository.save(
                new Comment(postId, userId, nickname, command.content()));
        postRepository.incrementCommentCount(postId);
        if (!userId.equals(post.getUserId())) {
            outboxEventRepository.save(
                    outboxEventPort.buildPostCommented(post, comment, userId));
        }
        return CommentResult.fromCreation(comment, nickname);
    }
}
