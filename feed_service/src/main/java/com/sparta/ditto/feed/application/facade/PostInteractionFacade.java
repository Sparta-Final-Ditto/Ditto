package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.feed.application.dto.command.CreateCommentCommand;
import com.sparta.ditto.feed.application.dto.result.CommentResult;
import com.sparta.ditto.feed.application.dto.result.LikeResult;
import com.sparta.ditto.feed.application.service.BlockCheckService;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.domain.exception.BlockedRelationException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 좋아요·댓글 생성의 접근제어(visibility)와 차단 검증을 조율하는 퍼사드(트랜잭션 없음).
 *
 * <p>{@link PostCreateFacade} 선례를 따라, 외부 Feign I/O(접근제어의 팔로우 확인, 차단 검증)를
 * DB 트랜잭션 밖에서 먼저 수행한 뒤 {@link PostInteractionService}의 {@code @Transactional} 생성 메서드에
 * 위임한다. 진입 메서드와 트랜잭션 메서드를 서로 다른 빈에 두어 self-invocation 무력화를 방지한다.</p>
 *
 * <p>순서: ① 접근제어({@link PostAccessGuard}) — 게시글 존재+visibility 검증(불가 시 404, 존재 은닉)
 * → ② 본인 글이면 차단 검증 생략 → ③ 차단 검증(true면 {@link BlockedRelationException} 변환, 403)
 * → ④ 기존 생성 로직 위임.</p>
 */
@Component
@RequiredArgsConstructor
public class PostInteractionFacade {

    private final PostAccessGuard postAccessGuard;
    private final BlockCheckService blockCheckService;
    private final PostInteractionService postInteractionService;

    public LikeResult addLike(UUID userId, UUID postId, String nickname) {
        UUID ownerId = postAccessGuard.requireAccessiblePostOwner(postId, userId);
        requireNotBlocked(userId, ownerId);
        return postInteractionService.addLike(userId, postId, nickname);
    }

    public CommentResult createComment(
            UUID userId, String nickname, UUID postId, CreateCommentCommand command) {
        UUID ownerId = postAccessGuard.requireAccessiblePostOwner(postId, userId);
        requireNotBlocked(userId, ownerId);
        return postInteractionService.createComment(userId, nickname, postId, command);
    }

    private void requireNotBlocked(UUID requesterId, UUID ownerId) {
        if (requesterId.equals(ownerId)) {
            return;
        }
        if (blockCheckService.isBlockedEitherDirection(requesterId, ownerId)) {
            throw new BlockedRelationException();
        }
    }
}