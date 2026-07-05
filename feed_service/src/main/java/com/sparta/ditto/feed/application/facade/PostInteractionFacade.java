package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.feed.application.dto.command.CreateCommentCommand;
import com.sparta.ditto.feed.application.dto.result.CommentResult;
import com.sparta.ditto.feed.application.dto.result.LikeResult;
import com.sparta.ditto.feed.application.service.BlockCheckService;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.exception.BlockedRelationException;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 좋아요·댓글 생성의 차단 검증을 조율하는 퍼사드(트랜잭션 없음).
 *
 * <p>{@link com.sparta.ditto.feed.application.facade.PostCreateFacade} 선례를 따라, 외부 Feign I/O(차단 검증)를
 * DB 트랜잭션 밖에서 먼저 수행한 뒤 {@link PostInteractionService}의 {@code @Transactional} 생성 메서드에
 * 위임한다. 진입 메서드와 트랜잭션 메서드를 서로 다른 빈에 두어 self-invocation 무력화를 방지한다.</p>
 *
 * <p>순서: ① 게시글 존재+작성자 조회(없으면 404, 차단 403보다 우선) → ② 본인 글이면 차단 검증 생략
 * → ③ 차단 검증(true면 여기서 {@link BlockedRelationException} 변환) → ④ 기존 생성 로직 위임.</p>
 */
@Component
@RequiredArgsConstructor
public class PostInteractionFacade {

    private final PostRepository postRepository;
    private final BlockCheckService blockCheckService;
    private final PostInteractionService postInteractionService;

    public LikeResult addLike(UUID userId, UUID postId, String nickname) {
        UUID ownerId = requireExistingPostOwner(postId);
        requireNotBlocked(userId, ownerId);
        return postInteractionService.addLike(userId, postId, nickname);
    }

    public CommentResult createComment(
            UUID userId, String nickname, UUID postId, CreateCommentCommand command) {
        UUID ownerId = requireExistingPostOwner(postId);
        requireNotBlocked(userId, ownerId);
        return postInteractionService.createComment(userId, nickname, postId, command);
    }

    private UUID requireExistingPostOwner(UUID postId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);
        return post.getUserId();
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
